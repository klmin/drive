package com.thinkfree.drive.api.v1.admin.sharesetting.usecase;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.api.v1.admin.sharesetting.converter.AdminShareSettingRequestConverter;
import com.thinkfree.drive.api.v1.admin.sharesetting.request.AdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.admin.sharesetting.response.AdminShareSettingResponse;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.cleanup.share.global.service.GlobalShareReconcileService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.util.SecurityUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminShareSettingUseCase {

    private final ShareSettingFacade shareSettingFacade;
    private final ShareSettingService shareSettingService;
    private final SystemConfigService systemConfigService;
    private final GlobalShareReconcileService globalShareReconcileService;
    private final AdminShareSettingRequestConverter requestConverter;
    private final SettingAuditService settingAuditService;

    /**
     * 테넌트 어드민 공유 설정 조회.
     * 슈퍼어드민이 전체 적용 중이면 글로벌 설정을 보여주고 {@code isEnforced=true} 로 폼을 잠근다.
     */
    @Transactional(readOnly = true)
    public AdminShareSettingResponse get() {
        int tenantSeq = TenantContext.getOrThrow();
        var resolved = shareSettingFacade.resolveWithEnforced(tenantSeq);
        var effective = resolved.effective();

        return new AdminShareSettingResponse(
                resolved.enforced(),
                effective.shareEnabled(),
                effective.externalShareAllowed(),
                effective.specificUsersAllowed(),
                effective.orgLinkAllowed(),
                effective.publicAllowed()
        );
    }

    /**
     * 테넌트 어드민 공유 설정 등록/수정. (슈퍼어드민 upsert 와 동일 구조, 단일 테넌트 / enforce 없음)
     * <pre>
     * - 슈퍼어드민 전체 적용 중이면 변경 불가(409).
     * - 공유 off(se=false): external off + scope 보존(upsertTenantShareOff) + share_status=false + reconcile
     * - 공유 on(se=true)  : 권한 4개 저장(upsertTenant)              + share_status=true  + reconcile
     * </pre>
     */
    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SHARE)
    public void save(AdminShareSettingSaveRequest request) {

        int tenantSeq = TenantContext.getOrThrow();
        var resolved = shareSettingFacade.resolveWithEnforced(tenantSeq);

        DriveAssert.throwIfTrue(resolved.enforced(), ShareErrorCode.SUPER_ADMIN_SHARE_ENFORCED);

        EffectiveShareSetting old = resolved.effective();

        boolean shareOn = Boolean.TRUE.equals(request.se());

        if (shareOn) {
            shareSettingService.upsertTenant(requestConverter.toTenantUpsertDto(request, tenantSeq));
        } else {
            shareSettingService.upsertTenantShareOff(tenantSeq);
        }

        systemConfigService.upsertShareStatus(tenantSeq, shareOn, SecurityUtil.getUserSeq());

        EffectiveShareSetting now = new EffectiveShareSetting(
                shareOn,
                Boolean.TRUE.equals(request.esa()),
                Boolean.TRUE.equals(request.sua()),
                Boolean.TRUE.equals(request.ola()),
                Boolean.TRUE.equals(request.pa())
        );

        settingAuditService.recordExternalShareChangeIfNeeded(old.externalShareAllowed(), now.externalShareAllowed());

        globalShareReconcileService.reconcile(tenantSeq, old, now);
    }
}
