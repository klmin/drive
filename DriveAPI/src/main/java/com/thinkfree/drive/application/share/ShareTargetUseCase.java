package com.thinkfree.drive.application.share;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.application.share.command.ShareTargetReplaceCommand;
import com.thinkfree.drive.application.share.command.SharedListCommand;
import com.thinkfree.drive.application.share.converter.ShareTargetCommandConverter;
import com.thinkfree.drive.application.share.converter.SharedCommandConverter;
import com.thinkfree.drive.application.share.converter.SharedInfoConverter;
import com.thinkfree.drive.application.share.info.SharedListInfo;
import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.application.share.validation.ShareWriteValidator;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService;
import com.thinkfree.drive.auditlog.service.ShareAuditService;
import com.thinkfree.drive.cleanup.share.dto.ShareCleanUpDto;
import com.thinkfree.drive.cleanup.share.service.ShareCleanUpService;
import com.thinkfree.drive.event.share.target.ShareTargetReplacedEvent;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessShareTargetChangeDto;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.target.data.ShareTargetResourceData;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.share.target.exception.ShareTargetErrorCode;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;
import com.thinkfree.drive.share.target.service.ShareTargetFindService;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.util.UserUtil;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 진입점(내부 API·MCP) 공용 공유 대상(share_target) usecase — 공유문서함(공유받은 리소스) 목록과
 * 공유 대상 교체(전체 치환)를 담당한다. 구 api.v1 ShareTargetUseCase의 이관.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 목록은 요청자 본인 대상 데이터만 조회하므로 웹과 마찬가지로 권한 검사가 없고,
 * 대상 교체의 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckSharePermission), MCP는 툴의 명시 호출.
 * CustomUserDetails는 (requestUserSeq, userId, userName)으로 해체해 받는다.
 */
@Service
@RequiredArgsConstructor
public class ShareTargetUseCase {

    private final SharedCommandConverter sharedCommandConverter;
    private final SharedInfoConverter sharedInfoConverter;
    private final ShareTargetCommandConverter shareTargetCommandConverter;
    private final ShareTargetFindService shareTargetFindService;
    private final ShareTargetService shareTargetService;
    private final ShareFindService shareFindService;
    private final ApplicationEventPublisher publisher;
    private final ShareCleanUpService shareCleanUpService;
    private final PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    private final ShareWriteValidator shareWriteValidator;
    private final ShareAuditLookupService shareAuditLookupService;
    private final ShareAuditService shareAuditService;
    private final UserFindService userFindService;

    @TenantFeatureRequired(TenantFeature.SHARE)
    public CursorPageInfo<SharedListInfo> findAllPaginated(SharedListCommand command, int requestUserSeq) {
        var query = sharedCommandConverter.toListQuery(command, requestUserSeq);
        var list = shareTargetFindService.findAll(query);

        // SQL은 sortBy·sortOrder 미지정 시 SHARE(공유일) DESC로 정렬하므로 커서 값 추출도 같은 기본값을 따른다
        ShareTargetSortField sortField = command.sortBy() != null ? command.sortBy() : ShareTargetSortField.SHARE;
        return CursorPageInfo.of(sharedInfoConverter.toListInfos(list), list, command.pageSize(),
                sortField.getExtractor()::apply, ShareTargetResourceData::getResourceSeq, ShareTargetResourceData::getResourceType);
    }

    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SHARE)
    public void replaceShareTargets(Long shareSeq, ShareTargetReplaceCommand command, int requestUserSeq, String userId, String userName) {

        // targets 미제공(null)이면 빈 목록으로 정규화 → 이후 "전체 삭제" 경로로 흐름 (모든 유형 허용)
        List<ShareTargetItemCommand> ts = command.targets() == null ? List.of() : command.targets();

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var sharedResource = shareFindService.findSharedResourceByShare(shareSeq, userSeq, false);
        int shareUserSeq = sharedResource.getUserSeq();
        var resourceSeq = sharedResource.getResourceSeq();

        // 감사 로그의 SHARE_REMOVE(해제 전 권한)·SHARE_PERMISSION_CHANGE(변경 전 권한)는 치환 뒤엔 조회할 수 없어 미리 떠 둔다.
        var beforeShareState = shareAuditLookupService.findCurrentShareState(shareSeq, userSeq);

        // 공유 설정 검증(shareEnabled/scope) + 대상 검증(capability/존재/external). 모든 유형에서 빈 대상(전체 삭제) 허용.
        var userDataList = shareWriteValidator.validateTargetWrite(shareUserSeq, sharedResource.getShareType(), ts, ShareTargetErrorCode.INSERT_FAIL);

        var targets = shareTargetCommandConverter.toShareTargetUpsertDtoList(ts, sharedResource.getShareSeq(), resourceSeq, userSeq);
        var dto = shareTargetCommandConverter.toShareTargetsReplaceDto(shareSeq, userSeq, sharedResource.getShareType(), sharedResource.getResourceType(), resourceSeq, targets);
        var replaceResult = shareTargetService.replaceShareTargets(dto);
        var addUserSeqSet = replaceResult.addedUserSeqSet();

        var actorTenantSeq = TenantContext.getOrThrow();
        var postProcessShareTargetChangeDto = new PostProcessShareTargetChangeDto(userSeq, userName, resourceSeq, sharedResource.getResourceName(), addUserSeqSet,
                replaceResult.shareTargetDeletionResult().deleteShareTargetUserSeqSet(), requestUserSeq, replaceResult.occurredDatetime(), actorTenantSeq);

        postProcessingOrchestratorRetry.processShareTargetChangedAndNotify(postProcessShareTargetChangeDto);

        recordTargetReplaceAudit(sharedResource.getResourceName(), resourceSeq, userSeq, actorTenantSeq,
                beforeShareState, ts, targets, userDataList, addUserSeqSet,
                replaceResult.shareTargetDeletionResult().deleteShareTargetUserSeqSet());

        // 대상 교체 정리는 공유 유형과 무관하게 per-user(대상) 방식 → SPECIFIC_USERS 로 cleanup 라우팅 (update 의 대상 교체 채널과 동일).
        // 링크 공유여도 keepsLinkAccess(ORG=같은테넌트/PUBLIC=전원)로 접근 유지되는 대상은 calculateTargetsDeleteResult 가 이미 메타 정리에서 제외.
        var createShareClanUpDto = createShareClanUpDto(shareUserSeq, ShareType.SPECIFIC_USERS, replaceResult.shareTargetDeletionResult());
        shareCleanUpService.deleteShareCascade(createShareClanUpDto);

        var mailUserSeqSet = ShareTargetValidationItem.resolveMailUserSeqSet(ts, addUserSeqSet);

        if(mailUserSeqSet.isEmpty()){
            return;
        }

        var shareTargetUserEmails = UserUtil.extractUserIds(mailUserSeqSet, userDataList);
        var locale = LocaleContextHolder.getLocale();

        ShareTargetReplacedEvent shareTargetReplacedEvent = new ShareTargetReplacedEvent(TenantContext.getOrThrow(), userId, userName, sharedResource.getResourceType(), sharedResource.getResourceName(), shareTargetUserEmails, locale);
        publisher.publishEvent(shareTargetReplacedEvent);

    }

    /**
     * 대상 교체 결과를 감사 로그로 남긴다 — 추가는 SHARE_CREATE, 제거는 SHARE_REMOVE, 유지되며 권한만 바뀐 건은
     * SHARE_PERMISSION_CHANGE. 공유 범위(링크 자체)는 이 API가 건드리지 않아 해당 row가 없다.
     * <p>제거된 대상자는 새 대상 목록에 없어 테넌트 정보를 얻으려면 따로 조회해야 한다.
     *
     * @param requestedTargets 요청이 부여한 권한(SHARE_CREATE의 afterValue)
     * @param replacedTargets 저장 직전 대상 dto(변경 전 권한과 비교해 SHARE_PERMISSION_CHANGE를 가려낸다)
     */
    private void recordTargetReplaceAudit(String resourceName, Long resourceSeq, int shareOwnerUserSeq, Integer actorTenantSeq,
                                          ShareWithTargetsData beforeShareState, List<ShareTargetItemCommand> requestedTargets,
                                          List<ShareTargetUpsertDto> replacedTargets, List<UserData> userDataList,
                                          Set<Integer> addedUserSeqSet, Set<Integer> removedUserSeqSet) {

        var permissionChanges = shareAuditService.targetPermissionChanges(beforeShareState.getTargets(), replacedTargets);
        boolean added = addedUserSeqSet != null && !addedUserSeqSet.isEmpty();
        boolean removed = removedUserSeqSet != null && !removedUserSeqSet.isEmpty();

        if (!added && !removed && permissionChanges.isEmpty()) {
            return;
        }

        var resourcePath = shareAuditLookupService.findResourcePath(resourceSeq, shareOwnerUserSeq);

        if (added) {
            shareAuditService.recordAll(EventName.SHARE_CREATE, addedUserSeqSet, userDataList, resourceSeq, resourceName, resourcePath, actorTenantSeq,
                    shareAuditService.grantedPermissionByUserSeq(requestedTargets));
        }

        if (removed) {
            var removedUserDataList = userFindService.findVisibleUsers(new ArrayList<>(removedUserSeqSet));
            shareAuditService.recordAll(EventName.SHARE_REMOVE, removedUserSeqSet, removedUserDataList, resourceSeq, resourceName, resourcePath, actorTenantSeq,
                    shareAuditService.currentPermissionByUserSeq(beforeShareState.getTargets()));
        }

        for (var change : permissionChanges) {
            shareAuditService.recordTargetPermissionChange(resourceSeq, resourceName, resourcePath, change, userDataList, actorTenantSeq);
        }
    }

    private ShareCleanUpDto createShareClanUpDto(int sharedByUserSeq, ShareType shareType, ShareTargetDeletionResult deleteResult) {
        return new ShareCleanUpDto(sharedByUserSeq, shareType, deleteResult.cleanUpPartitionKeys(), deleteResult.cleanUpUserResourceGroups(), deleteResult.deleteDescendants(), Set.of());
    }

}
