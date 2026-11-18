package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 규칙 표 — 유형·모드가 추가·변경되면 이 표만 고친다(호출부는 {@link TenantFeature} 만 안다).
 * <pre>
 * 기능                                                                                 | SELF_MANAGED | SYSTEM_MANAGED+MULTI | SYSTEM_MANAGED+SINGLE
 * LICENSE_MANAGE·SYSTEM_CONFIG·LOGIN_SETTING·SHARE·MAIL·ACCESS_RESTRICTION·MFA_SETTING | 허용         | 차단                  | 차단
 * STORAGE_INFO_VIEW·SYSTEM_INFO_VIEW·SMTP_INFO_VIEW(조회 응답 값 마스킹)              | 허용         | 추가 마스킹           | 추가 마스킹
 * USER_REGISTER·API_KEY_MANAGE·SUB_ACCOUNT_MANAGE                                      | 허용         | 허용                  | 차단
 * API_KEY_INFO_VIEW·OFFICE_INFO_VIEW(조회 응답 값 마스킹)                              | 허용         | 허용                  | 추가 마스킹
 * 보호 계정 관리(탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경)                          | 차단         | 차단                  | 차단
 * 보호 계정 본인 비밀번호 변경(변경·재설정 링크·토큰 재설정)                                  | 허용         | 차단                  | 차단
 * 보호 계정 정보 수정(관리자 단건·bulk 수정 — 이름·용량·연락처·프로필 이미지·역할)                | 허용         | 차단                  | 차단
 * </pre>
 * 유형·사용자 모드는 매 판정마다 테넌트를 조회한다(PK 한 행) — 판정 대상 tenantSeq 가 요청 테넌트가 아닌 경우가 많고, 모드 전환·클러스터 정합성을 위해 캐시하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DefaultTenantPolicy implements TenantPolicy {

    /** SYSTEM_MANAGED 이면 모드와 무관하게 막는 기능 */
    private static final Set<TenantFeature> SYSTEM_MANAGED_BLOCKED =
            EnumSet.of(TenantFeature.LICENSE_MANAGE, TenantFeature.SYSTEM_CONFIG, TenantFeature.LOGIN_SETTING, TenantFeature.SHARE,
                       TenantFeature.MAIL, TenantFeature.ACCESS_RESTRICTION, TenantFeature.MFA_SETTING);
    /** SYSTEM_MANAGED 이면 조회 응답의 값을 마스킹하는 기능 — allows 는 false, 마스킹은 호출부가 처리 */
    private static final Set<TenantFeature> SYSTEM_MANAGED_MASKED =
            EnumSet.of(TenantFeature.STORAGE_INFO_VIEW, TenantFeature.SYSTEM_INFO_VIEW, TenantFeature.SMTP_INFO_VIEW);
    /** SYSTEM_MANAGED + SINGLE 에서 추가로 막는 기능 */
    private static final Set<TenantFeature> SYSTEM_MANAGED_SINGLE_BLOCKED =
            EnumSet.of(TenantFeature.USER_REGISTER, TenantFeature.API_KEY_MANAGE, TenantFeature.SUB_ACCOUNT_MANAGE);
    /** SYSTEM_MANAGED + SINGLE 에서 조회 응답의 값을 마스킹하는 기능 — allows 는 false, 마스킹은 호출부가 처리 */
    private static final Set<TenantFeature> SYSTEM_MANAGED_SINGLE_MASKED = EnumSet.of(TenantFeature.API_KEY_INFO_VIEW, TenantFeature.OFFICE_INFO_VIEW);

    private final TenantFindService tenantFindService;
    private final UserFindService userFindService;

    /**
     * {@link #allows} 가 false 가 되는 테넌트 조건(OR) — 문서용({@link TenantRestriction}). 위 집합에서 만들므로 판정과 어긋나지 않는다.
     * @return 막지 않는 기능이면 빈 목록
     */
    public static List<TenantRestrictionCondition> restrictedWhen(TenantFeature feature) {
        if (SYSTEM_MANAGED_BLOCKED.contains(feature) || SYSTEM_MANAGED_MASKED.contains(feature)) {
            return List.of(new TenantRestrictionCondition(TenantType.SYSTEM_MANAGED, null, false));
        }
        if (SYSTEM_MANAGED_SINGLE_BLOCKED.contains(feature) || SYSTEM_MANAGED_SINGLE_MASKED.contains(feature)) {
            return List.of(new TenantRestrictionCondition(TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE, false));
        }
        return List.of();
    }

    /**
     * 허용되지 않을 때 거부 대신 조회 응답 값을 가리는 기능이면 true — 문서용({@link TenantRestriction} 의 maskedWhen).
     * 이런 기능도 {@link #allows} 는 false 이고, 거부할지 가릴지는 호출부가 정한다(가리는 쪽이 이 집합).
     */
    public static boolean isMasked(TenantFeature feature) {
        return SYSTEM_MANAGED_MASKED.contains(feature) || SYSTEM_MANAGED_SINGLE_MASKED.contains(feature);
    }

    /** 보호 계정 판정이 거부하는 조건(OR) — 문서용({@link TenantRestriction}). 규칙은 각 require* 메서드와 같다 */
    public static List<TenantRestrictionCondition> restrictedWhen(ProtectedAccountRule rule) {
        return switch (rule) {
            case MANAGE -> List.of(new TenantRestrictionCondition(null, null, true));
            case UPDATE, SELF_PASSWORD -> List.of(new TenantRestrictionCondition(TenantType.SYSTEM_MANAGED, null, true));
        };
    }

    @Override
    public boolean allows(Integer tenantSeq, TenantFeature feature) {
        if (tenantSeq == null) {
            return true;
        }
        TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
        if (tenant.getTenantType() != TenantType.SYSTEM_MANAGED) {
            return true;
        }
        if (SYSTEM_MANAGED_BLOCKED.contains(feature) || SYSTEM_MANAGED_MASKED.contains(feature)) {
            return false;
        }
        return !(tenant.getUserMode() == TenantUserMode.SINGLE
                && (SYSTEM_MANAGED_SINGLE_BLOCKED.contains(feature) || SYSTEM_MANAGED_SINGLE_MASKED.contains(feature)));
    }

    @Override
    public void require(Integer tenantSeq, TenantFeature feature) {
        DriveAssert.throwIfFalse(allows(tenantSeq, feature), TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
    }

    @Override
    public void requireManageableUser(int userSeq) {
        DriveAssert.throwIfTrue(userFindService.findTenantSeqIfProtected(userSeq) != null, UserErrorCode.PROTECTED_USER);
    }

    @Override
    public void requireSelfPasswordChangeable(int userSeq) {
        DriveAssert.throwIfFalse(isSelfPasswordChangeable(userSeq), TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
    }

    @Override
    public boolean isSelfPasswordChangeable(int userSeq) {
        return !isSystemManagedProtected(userSeq);
    }

    @Override
    public void requireUpdatableUser(int userSeq) {
        DriveAssert.throwIfTrue(isSystemManagedProtected(userSeq), TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
    }

    /** SYSTEM_MANAGED 테넌트의 보호 계정 — 값(비밀번호·계정 정보)을 외부 시스템이 정하는 계정 */
    private boolean isSystemManagedProtected(int userSeq) {
        Integer protectedTenantSeq = userFindService.findTenantSeqIfProtected(userSeq);
        return protectedTenantSeq != null
                && tenantFindService.findByTenantSeq(protectedTenantSeq).getTenantType() == TenantType.SYSTEM_MANAGED;
    }
}
