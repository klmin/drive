package com.thinkfree.drive.application.share.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.share.data.SharedResourceNearestData;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 공유 등록/수정/대상등록(share insert·update, share_target save) 공통 쓰기 검증.
 * <p>
 * 검증은 소유주 테넌트의 공유 설정 기준으로 해소하며, 위반 시 즉시 실패(하드 차단)한다.
 * resource 조회 / nearest 공유 조회(쿼리가 흐름마다 다름)는 각 usecase 책임으로 남기고,
 * "설정/공유유형/권한/대상" 검증만 여기로 모은다.
 * (구 api.v1.share.validation 소속 — 어댑터 색이 없는 도메인 검증이라 share.validation으로 이동,
 *  application usecase가 참조할 수 있게 함)
 */
@Component
@RequiredArgsConstructor
public class ShareWriteValidator {

    private final ShareSettingFacade shareSettingFacade;
    private final UserFindService userFindService;

    /**
     * 공유 등록/수정 검증 (전역 설정 → 공유유형 → 권한 → 대상). 검증된 대상 유저 목록을 반환한다.
     *
     * @param nearest      상속(상위/하위) 공유 정보 — 있으면 그 유형으로 고정
     * @param ownerUserSeq 리소스 소유주 userSeq (설정 해소/외부 판정 기준)
     * @param request      공유 등록/수정 요청 (권한 플래그 + 대상)
     * @param errorCode    대상/권한 검증 실패 시 에러코드 (등록=INSERT_FAIL, 수정=UPDATE_FAIL)
     * @return 검증된 대상 유저 목록 (대상이 없으면 빈 목록)
     */
    public List<UserData> validateAndResolveTargets(SharedResourceNearestData nearest, int ownerUserSeq, ShareValidationRequest request, ErrorCode errorCode) {

        var shareType = request.shareType();

        // 1. 전역(공유 설정): shareEnabled 를 가장 먼저 fail-fast, 그다음 공유유형 scope 허용 여부
        var resolved = shareSettingFacade.resolveByUserSeq(ownerUserSeq);
        var effective = resolved.effective();
        DriveAssert.throwIfFalse(effective.shareEnabled(), ShareErrorCode.SHARE_DISABLED);
        DriveAssert.throwIfFalse(isScopeAllowed(effective, shareType), ShareErrorCode.SHARE_TYPE_NOT_ALLOWED);

        // 2. 상속 공유유형 고정 (상위/하위에 공유가 있으면 그 유형만 허용)
        validShareType(nearest, shareType);

        // 3. 링크 공유(org/public)는 공유 행(링크) 권한 플래그 필수
        if(shareType.isPublic()){
            DriveAssert.throwIfAnyNull(errorCode, request.canView(), request.canEdit(), request.canDownload(), request.canUpload(), request.canShare());
        }

        // 4. 대상 유저 (모든 유형에서 대상 없이 허용)
        return resolveTargets(request.targets(), ownerUserSeq, resolved.tenantSeq(), effective, errorCode);
    }

    /**
     * 대상 유저 검증 (제한됨 capability → 존재검사 → external). 검증된 대상 유저 목록을 반환한다.
     * 공유 등록/수정과 공유 대상 등록(PUT targets)이 공유한다.
     *
     * @param targets       대상 유저 목록 (비어있지 않아야 함)
     * @param ownerUserSeq  소유주 userSeq (self 제외 기준)
     * @param ownerTenantSeq 소유주 테넌트 (external 판정 기준)
     * @param effective     소유주 테넌트의 실효 공유 설정
     * @param errorCode     검증 실패 시 에러코드
     */
    public List<UserData> validateTargetUsers(List<? extends ShareTargetValidationItem> targets,
                                              int ownerUserSeq, int ownerTenantSeq, EffectiveShareSetting effective, ErrorCode errorCode) {

        // 개별 대상을 얹으려면(모든 유형 공통) 제한됨(specific) 허용 필요 — scope이자 capability
        DriveAssert.throwIfFalse(effective.specificUsersAllowed(), ShareErrorCode.SPECIFIC_USERS_NOT_ALLOWED);

        var userSeqList = targets.stream().map(item -> resolveValidTargetUserSeq(item.userSeq(), ownerUserSeq, errorCode)).toList();
        var userDataList = userFindService.findVisibleUsers(userSeqList);
        DriveAssert.throwIfFalse(userSeqList.size() == userDataList.size(), errorCode);

        // 외부(다른 테넌트) 대상이 포함되면 external 허용 필요
        boolean hasExternalTarget = userDataList.stream().anyMatch(u -> !Objects.equals(u.getTenantSeq(), ownerTenantSeq));
        if(hasExternalTarget){
            DriveAssert.throwIfFalse(effective.externalShareAllowed(), ShareErrorCode.EXTERNAL_SHARE_NOT_ALLOWED);
        }

        return userDataList;
    }

    /**
     * 공유 대상 수정(PUT targets) 검증. 대상만 교체되고 공유(share) 자체·유형은 안 바뀌므로,
     * 기존 공유의 shareType 으로 scope 를 검증한 뒤 대상 유저를 검증한다.
     * <p>
     * ⚠️ {@link #validateTargetUsers}는 shareEnabled/scope 를 보지 않으므로 대상 수정 경로에선 반드시 이 메서드로 감싼다.
     *
     * @param ownerUserSeq 리소스 소유주 userSeq (설정 해소/외부 판정 기준)
     * @param shareType    기존 공유 유형 (scope 검증용 — 대상 수정은 유형을 바꾸지 않음)
     * @param targets      교체할 대상 목록
     * @param errorCode    검증 실패 시 에러코드
     * @return 검증된 대상 유저 목록 (대상이 없으면 빈 목록)
     */
    public List<UserData> validateTargetWrite(int ownerUserSeq, ShareType shareType, List<? extends ShareTargetValidationItem> targets, ErrorCode errorCode) {

        // 1. 전역(공유 설정): shareEnabled → 기존 공유유형 scope 허용 여부 (설정이 좁혀졌으면 같은 유형 대상 수정도 차단)
        var resolved = shareSettingFacade.resolveByUserSeq(ownerUserSeq);
        var effective = resolved.effective();
        DriveAssert.throwIfFalse(effective.shareEnabled(), ShareErrorCode.SHARE_DISABLED);
        DriveAssert.throwIfFalse(isScopeAllowed(effective, shareType), ShareErrorCode.SHARE_TYPE_NOT_ALLOWED);

        // 2. 대상 유저 (모든 유형에서 대상 없이 허용 — 빈 대상 = 기존 대상 전체 삭제)
        return resolveTargets(targets, ownerUserSeq, resolved.tenantSeq(), effective, errorCode);
    }

    /**
     * 대상 유저 검증 공통 (등록/수정/대상수정 공유).
     * 모든 유형(제한됨 포함)에서 대상 없이 허용 — 수정 경로에선 빈 대상 = 기존 대상 전체 삭제.
     */
    private List<UserData> resolveTargets(List<? extends ShareTargetValidationItem> targets,
                                          int ownerUserSeq, int ownerTenantSeq, EffectiveShareSetting effective, ErrorCode errorCode) {

        if(targets == null || targets.isEmpty()){
            return Collections.emptyList();
        }
        return validateTargetUsers(targets, ownerUserSeq, ownerTenantSeq, effective, errorCode);
    }

    private boolean isScopeAllowed(EffectiveShareSetting effective, ShareType shareType) {
        return switch (shareType) {
            case SPECIFIC_USERS -> effective.specificUsersAllowed();
            case ORG_LINK -> effective.orgLinkAllowed();
            case PUBLIC_LINK -> effective.publicAllowed();
        };
    }

    private void validShareType(SharedResourceNearestData nearest, ShareType shareType) {
        if(nearest == null){
            return;
        }
        if(shareType != nearest.getShareType()){
            throw ApiRuntimeException.type(ShareErrorCode.INVALID_SHARE_TYPE);
        }
    }

    private int resolveValidTargetUserSeq(int userSeq, int shareUserSeq, ErrorCode errorCode) {
        DriveAssert.throwIfTrue(userSeq == shareUserSeq, errorCode);
        return userSeq;
    }
}
