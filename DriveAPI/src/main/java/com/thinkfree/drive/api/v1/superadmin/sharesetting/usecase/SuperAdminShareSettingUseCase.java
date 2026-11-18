package com.thinkfree.drive.api.v1.superadmin.sharesetting.usecase;

import com.thinkfree.drive.api.v1.admin.systemconfig.response.ShareExistsResponse;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.converter.SuperAdminShareSettingRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.request.SuperAdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.response.SuperAdminShareSettingResponse;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.cleanup.share.global.service.GlobalShareReconcileService;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.setting.data.ShareSettingData;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import com.thinkfree.drive.share.setting.service.ShareSettingFindService;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SuperAdminShareSettingUseCase {

    private final ShareSettingFindService shareSettingFindService;
    private final ShareSettingService shareSettingService;
    private final ShareSettingFacade shareSettingFacade;
    private final SystemConfigFindService systemConfigFindService;
    private final SystemConfigService systemConfigService;
    private final TenantFindService tenantFindService;
    private final GlobalShareReconcileService globalShareReconcileService;
    private final SuperAdminShareSettingRequestConverter requestConverter;
    private final SettingAuditLookupService settingAuditLookupService;
    private final SettingAuditService settingAuditService;
    private final ShareFindService shareFindService;

    @Transactional(readOnly = true)
    public SuperAdminShareSettingResponse get() {
        
        ShareSettingData global = shareSettingFindService.findGlobal();
        
        if (global == null) {
            return new SuperAdminShareSettingResponse(null,null,null,null,null,null,null);
        }
        
        boolean shareEnabled = systemConfigFindService.findGlobalShareStatusConfig().isEnabled();
        
        return new SuperAdminShareSettingResponse(
                global.shareSettingSeq(),
                global.enforced(),
                shareEnabled,
                global.externalShareAllowed(),
                global.specificUsersAllowed(),
                global.orgLinkAllowed(),
                global.publicAllowed());
    }

    /**
     * 슈퍼어드민(글로벌) 공유 설정 등록/수정.
     * <pre>
     * 1) 개별적용(e=false)      : enforce 만 false 로. 상세 flag·글로벌 share_status 그대로, cleanup X
     *                            (테넌트가 각자 설정을 쓰는 완화이므로 일괄 삭제 대상 아님)
     * 2) 전체+공유 off(se=false): enforce=true(상세 flag 보존) + share_status=false + cleanup(기존 비교)
     * 3) 전체+공유 on(se=true)  : enforce=true + 상세 flag 저장      + share_status=true  + cleanup(기존 비교)
     * </pre>
     */
    @AuditLog
    @Transactional
    public void upsert(SuperAdminShareSettingSaveRequest request) {

        boolean oldEnforced = settingAuditLookupService.isGlobalShareEnforced();
        boolean newEnforced = Boolean.TRUE.equals(request.e());

        if (!newEnforced) {
            shareSettingService.upsertGlobalEnforce(false);
            settingAuditService.recordGlobalSharePolicyChangeIfNeeded(oldEnforced, newEnforced);
            return;
        }

        Map<Integer, EffectiveShareSetting> oldEffectiveByTenant = captureEffectiveByTenant();

        boolean shareOn = Boolean.TRUE.equals(request.se());

        if (shareOn) {
            shareSettingService.upsertGlobal(requestConverter.toGlobalUpsertDto(request));
        } else {
            shareSettingService.upsertGlobalShareOff();
        }

        systemConfigService.upsertGlobalShareStatus(shareOn, SecurityUtil.getUserSeq());

        EffectiveShareSetting newGlobal = new EffectiveShareSetting(
                shareOn,
                Boolean.TRUE.equals(request.esa()),
                Boolean.TRUE.equals(request.sua()),
                Boolean.TRUE.equals(request.ola()),
                Boolean.TRUE.equals(request.pa())
        );

        settingAuditService.recordGlobalSharePolicyChangeIfNeeded(oldEnforced, newEnforced);

        globalShareReconcileService.reconcileAllTenants(oldEffectiveByTenant, newGlobal);
    }

    /** 전체적용 시 정리 대상인 조직 테넌트 중 공유가 하나라도 있는지. */
    @Transactional(readOnly = true)
    public ShareExistsResponse existsShare() {
        boolean exists = tenantFindService.findSelfManagedTenantSeqs().stream()
                .anyMatch(shareFindService::existsByTenant);
        return new ShareExistsResponse(exists);
    }

    private Map<Integer, EffectiveShareSetting> captureEffectiveByTenant() {
        Map<Integer, EffectiveShareSetting> snapshot = new LinkedHashMap<>();
        for (Integer tenantSeq : tenantFindService.findSelfManagedTenantSeqs()) {
            snapshot.put(tenantSeq, shareSettingFacade.resolve(tenantSeq));
        }
        return snapshot;
    }
}
