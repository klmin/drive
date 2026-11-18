package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.page.response.CursorPaginationResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.converter.SuperAdminTenantRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.tenant.converter.SuperAdminTenantResponseConverter;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantAccessTokenResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantCreateResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantDetailResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantListResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantQuotaCapacityResponse;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.cleanup.tenant.service.TenantDeletionService;
import com.thinkfree.drive.common.validation.DomainComposer;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.license.service.LicenseFindService;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.policy.TenantQuotaPolicy;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.storage.enums.CapacityMode;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuperAdminTenantUseCase {

    private static final long FIXED_ADMIN_QUOTA = 0L;

    private final TenantService tenantService;
    private final TenantFindService tenantFindService;
    private final BaseDomainFindService baseDomainFindService;
    private final UserService userService;
    private final AccessAllowedIpService accessAllowedIpService;
    private final SystemConfigService systemConfigService;
    private final ShareSettingService shareSettingService;
    private final LicenseFindService licenseFindService;
    private final SuperAdminTenantRequestConverter requestConverter;
    private final SuperAdminTenantResponseConverter responseConverter;
    private final TenantAccessTokenService tenantAccessTokenService;
    private final SettingAuditService settingAuditService;
    private final ClientDomainService clientDomainService;
    private final SystemConfigFindService systemConfigFindService;
    private final QuotaService quotaService;
    private final UserFindService userFindService;
    private final StorageAdminService storageAdminService;
    private final LicenseService licenseService;
    private final TenantDeletionService tenantDeletionService;

    public SuperAdminTenantDetailResponse find(int tenantSeq) {
        var tenant = tenantFindService.findByTenantSeq(tenantSeq);
        var allowedIps = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.ADMIN);
        var totalQuotaByte = systemConfigFindService.findTenantTotalQuotaByte(tenantSeq);
        return responseConverter.toDetailResponse(tenant, allowedIps, totalQuotaByte);
    }

    /**
     * 등록·수정 모달 총 용량 입력 검증용 값 — tenantSeq 가 null 이면 등록(테넌트 값 null).
     * Ceph 대시보드 호출 실패는 ExternalApiException 그대로 — 프론트는 인라인 검증만 건너뛰고 저장 시 서버 판정에 맡긴다.
     */
    public SuperAdminTenantQuotaCapacityResponse findQuotaCapacity(Integer tenantSeq) {
        Long tenantTotalQuotaByte = null;
        Long tenantAllocatedQuotaByte = null;
        Long tenantMinimumQuotaByte = null;
        if (tenantSeq != null) {
            var tenant = tenantFindService.findByTenantSeq(tenantSeq); // 없는 테넌트면 TENANT_NOT_FOUND
            tenantTotalQuotaByte = systemConfigFindService.findTenantTotalQuotaByte(tenantSeq);
            tenantAllocatedQuotaByte = userFindService.sumTotalQuotaByteByTenantSeq(tenantSeq);
            tenantMinimumQuotaByte = isManagedSingle(tenant)
                    ? Math.max(1L, singleAccount(tenantSeq).getUseQuotaByte())
                    : Math.max(1L, tenantAllocatedQuotaByte);
        }

        StorageCapacityResult capacity = storageAdminService.getStorageCapacity();
        boolean limited = capacity.capacityMode() == CapacityMode.LIMITED;
        Long storageTotalByte = limited ? capacity.totalBytes() : null;
        Long storageAvailByte = limited ? capacity.totalAvailBytes() : null;

        return new SuperAdminTenantQuotaCapacityResponse(storageTotalByte, storageAvailByte, tenantTotalQuotaByte, tenantAllocatedQuotaByte, tenantMinimumQuotaByte);
    }

    public CursorPaginationResponse<SuperAdminTenantListResponse> findAllPaginated(CursorPagingRequest pagingRequest, TenantType tenantType) {
        var query = requestConverter.toTenantListQuery(pagingRequest, tenantType);
        var tenants = tenantFindService.findAll(query);
        var page = CursorApiPage.of(tenants, pagingRequest.ps(), null, TenantData::getTenantSeq, null);
        
        var response = tenants.stream()
                .map(tenant -> responseConverter.toListResponse(
                        tenant,
                        clientDomainService.getDomain(tenant.getTenantSeq()),
                        officeSummary(tenant.getTenantSeq()),
                        licenseFindService.getSummary(tenant.getTenantSeq())))
                .toList();
        return CursorPaginationResponse.of(response, page);
    }

    /** 목록 행의 Office 연결 요약 — 라이선스 요약처럼 행마다 읽는다(슈퍼관리자 목록은 페이지당 20행) */
    private SuperAdminTenantListResponse.OfficeInfo officeSummary(int tenantSeq) {
        return new SuperAdminTenantListResponse.OfficeInfo(
                systemConfigFindService.findOfficeConfig(tenantSeq).getOfficeConnectionType(),
                systemConfigFindService.isOfficeServiceManaged(tenantSeq));
    }

    /**
     * 테넌트 Office 설정 조회 — 슈퍼관리자 등록·수정 화면이 현재 값을 채운다. 저장은 develop {@code SuperAdminOfficeController}(PUT).
     * 관리자 조회와 달리 서비스 관리여도 토큰·서명키를 그대로 준다(슈퍼관리자가 넣은 값이고, PUT 이 서명키를 매번 받기 때문). 없는 테넌트면 TENANT_NOT_FOUND.
     */
    public OfficeResponse getOffice(int tenantSeq) {
        tenantFindService.findByTenantSeq(tenantSeq);
        var office = systemConfigFindService.findOfficeConfig(tenantSeq);
        return new OfficeResponse(office.getOfficeConnectionType(), office.getOfficeDomain(), office.getOfficeAuthMode(),
                office.getOfficeTicketServiceToken(), office.getOfficeHttpStorageAdapterName(),
                office.getOfficeHttpStorageRequestJwtSecret(), systemConfigFindService.isOfficeServiceManaged(tenantSeq));
    }

    /**
     * 테넌트 + 관리자 계정(보호 계정) + 기본 설정 생성.
     * SYSTEM_MANAGED 는 관리자 계정이 드라이브도 쓰므로 USER+ADMIN·테넌트 총 용량 전부를 받고(MULTI 는 관리자가 줄여 다른 계정에 나눈다),
     * ADMIN 허용 IP(ips)는 두지 않으며 공유는 off 로 시작한다.
     */
    @Transactional
    public SuperAdminTenantCreateResponse create(SuperAdminTenantCreateRequest request, Integer loginUserSeq) {

        TenantType tenantType = request.tenantTypeOrDefault();
        TenantUserMode userMode = request.userModeOrDefault();
        DriveAssert.throwIfTrue(tenantType == TenantType.SELF_MANAGED && userMode == TenantUserMode.SINGLE,
                TenantErrorCode.TENANT_USER_MODE_NOT_ALLOWED);
        boolean systemManaged = tenantType == TenantType.SYSTEM_MANAGED;

        // 총 용량 검증을 먼저 — 새 테넌트라 할당된 사용자 용량이 없으므로 스토리지(Ceph) 가용량만 본다. 거부되면 INSERT 없이 끝난다
        if (!canIncreaseStorage(request.tq())) {
            throw ApiRuntimeException.type(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        }

        var baseDomain = baseDomainFindService.findByBaseDomainSeqOrThrow(request.bs());
        String subdomain = DomainComposer.normalizeSubdomain(request.sd());
        String fullDomain = DomainComposer.compose(subdomain, baseDomain.getBaseDomain());
        
        var saveDto = requestConverter.toSaveDto(request, subdomain, baseDomain.getBaseDomainSeq(), fullDomain, tenantType, userMode);
        int newTenantSeq = tenantService.save(saveDto);

        // 비밀번호 원문 그대로 — 인코딩(Password.encrypt)은 UserService 내부에서 수행
        var adminSaveDto = new AdminUserSaveDto(
                request.e(),
                newTenantSeq,
                request.p(),
                request.n(),
                systemManaged ? request.tq() : FIXED_ADMIN_QUOTA,
                loginUserSeq,
                systemManaged ? Set.of(RoleType.USER, RoleType.ADMIN) : Set.of(RoleType.ADMIN),
                null,
                null
        );
        var admin = userService.registerTenantAdmin(adminSaveDto);

        if (!systemManaged) {
            accessAllowedIpService.replaceAllowedIps(newTenantSeq, RoleType.ADMIN, loginUserSeq, ipsOrEmpty(request.ips()));
        }

        systemConfigService.initializeDefaultsForTenant(newTenantSeq, request.tq(), loginUserSeq);

        shareSettingService.initializeDefaultForTenant(newTenantSeq);
        if (systemManaged) {
            // 관리자 공유 off 와 같은 저장(외부 공유 off + share_status=false) — 새 테넌트라 정리할 공유 없음
            shareSettingService.upsertTenantShareOff(newTenantSeq);
            systemConfigService.upsertShareStatus(newTenantSeq, false, loginUserSeq);
        }

        return new SuperAdminTenantCreateResponse(newTenantSeq, admin.userSeq());
    }

    @AuditLog
    @Transactional
    public void update(Integer tenantSeq, SuperAdminTenantUpdateRequest request, Integer loginUserSeq) {

        // 테넌트 행 잠금 + 용량 판정을 먼저 — 거부되면 나머지 변경 없이 끝난다
        changeTotalQuota(tenantSeq, request.tq(), loginUserSeq);

        var baseDomain = baseDomainFindService.findByBaseDomainSeqOrThrow(request.bs());
        String subdomain = DomainComposer.normalizeSubdomain(request.sd());
        String fullDomain = DomainComposer.compose(subdomain, baseDomain.getBaseDomain());

        var updateDto = requestConverter.toUpdateDto(tenantSeq, request, subdomain, baseDomain.getBaseDomainSeq(), fullDomain);
        tenantService.update(updateDto);

        // SYSTEM_MANAGED 는 ADMIN 허용 IP 를 두지 않는다 — ips 무시
        if (tenantFindService.findByTenantSeq(tenantSeq).getTenantType() == TenantType.SYSTEM_MANAGED) {
            return;
        }

        var beforeIps = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.ADMIN);
        // ips 는 전체 교체 — 생략(null)은 빈 배열과 같이 기존 허용 IP 를 모두 지운다(등록과 같은 의미)
        List<String> afterIps = ipsOrEmpty(request.ips());
        accessAllowedIpService.replaceAllowedIps(tenantSeq, RoleType.ADMIN, loginUserSeq, afterIps);

        settingAuditService.recordAllowedIpChangeIfNeeded(beforeIps, afterIps);
    }

    /** ips 는 선택 값 — 생략(null)은 허용 IP 없음(빈 목록)으로 본다. */
    private static List<String> ipsOrEmpty(List<String> ips) {
        return ips == null ? List.of() : ips;
    }

    /**
     * 테넌트 총 용량 변경 — 테넌트 행을 잠근 뒤(사용자 할당량 배정과 직렬화) 정책 판정, 증가분만 스토리지 가용량 검사.
     * 키가 없는 테넌트(데이터 누락)는 여기서 처음 설정해 복구한다.
     */
    private void changeTotalQuota(int tenantSeq, long totalQuotaByte, int loginUserSeq) {
        tenantService.lock(tenantSeq);

        var tenant = tenantFindService.findByTenantSeq(tenantSeq);
        Long currentQuotaByte = systemConfigFindService.findTenantTotalQuotaByte(tenantSeq);
        User singleUser = null;
        if (isManagedSingle(tenant)) {
            // Previously only the tenant limit changed. Protected accounts cannot be edited through
            // the tenant-admin API, leaving their quota stale. Only managed SINGLE tenants share
            // one quota. Hold tenant then user locks (the allocation lock order), and commit both
            // limits in update()'s transaction. Concurrent usage updates serialize on the user row.
            singleUser = userService.lockUserBySeq(singleAccount(tenantSeq).getUserSeq());
            DriveAssert.throwIfTrue(!Boolean.TRUE.equals(singleUser.getIsProtected())
                    || !Integer.valueOf(tenantSeq).equals(singleUser.getTenantSeq()),
                    TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
            TenantQuotaPolicy.validateUserQuota(totalQuotaByte);
            TenantQuotaPolicy.requireNotBelowAllocated(totalQuotaByte, singleUser.getUseQuotaByte());
        } else if (TenantQuotaPolicy.isUnchanged(currentQuotaByte, totalQuotaByte)) {
            return;
        }

        long allocatedQuotaByte = userFindService.sumTotalQuotaByteByTenantSeq(tenantSeq);
        if (singleUser == null) {
            TenantQuotaPolicy.requireNotBelowAllocated(totalQuotaByte, allocatedQuotaByte);
        }
        long increaseQuotaByte = TenantQuotaPolicy.increaseOf(currentQuotaByte, totalQuotaByte, allocatedQuotaByte);
        if (increaseQuotaByte > 0 && !canIncreaseStorage(increaseQuotaByte)) {
            throw ApiRuntimeException.type(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        }
        if (singleUser != null) {
            // Do not skip equal tenant limits: a repeated request repairs an old user allocation.
            singleUser.changeTotalQuota(totalQuotaByte);
        }
        systemConfigService.upsertTenantTotalQuotaByte(tenantSeq, totalQuotaByte, loginUserSeq);
    }

    private boolean isManagedSingle(TenantData tenant) {
        return tenant.getTenantType() == TenantType.SYSTEM_MANAGED
                && tenant.getUserMode() == TenantUserMode.SINGLE;
    }

    private com.thinkfree.drive.user.data.UserDetailData singleAccount(int tenantSeq) {
        var users = userFindService.findUndeletedDetailsByTenantSeq(tenantSeq);
        // Fail closed on inconsistent SINGLE data; never choose an arbitrary account.
        DriveAssert.throwIfTrue(users.size() != 1, TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        return users.get(0);
    }

    /**
     * 스토리지(Ceph) 가용량 확인 — 대시보드 호출 실패도 가용량 부족(QUOTA_004)으로 본다(사용자 일괄 등록과 같은 처리).
     * 원인은 예외 context 로 남겨 전역 핸들러가 기록한다. S3·RustFS 는 용량 조회가 무제한이라 항상 true.
     */
    private boolean canIncreaseStorage(long increaseQuotaByte) {
        try {
            return quotaService.canIncrease(increaseQuotaByte);
        } catch (ExternalApiException e) {
            throw ApiRuntimeException.type(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT)
                    .with("storageAdminError", e.getStatus() + " " + e.getMessage());
        }
    }

    /**
     * 테넌트 삭제 — 지금은 SYSTEM_MANAGED 만(SELF_MANAGED 는 TENANT_010, 필요해지면 이 검사만 푼다). 없으면 TENANT_NOT_FOUND.
     * 사용자 영구 삭제·테넌트 범위 행·tb_tenant 삭제는 한 트랜잭션, 스토리지·캐시는 커밋 후. 감사 로그 없음.
     */
    @Transactional
    public void delete(int tenantSeq, int loginUserSeq) {
        var tenant = tenantFindService.findByTenantSeq(tenantSeq);
        DriveAssert.throwIfTrue(tenant.getTenantType() != TenantType.SYSTEM_MANAGED, TenantErrorCode.TENANT_DELETE_NOT_ALLOWED);
        tenantDeletionService.deleteTenant(tenantSeq, loginUserSeq);
    }

    /**
     * 라이선스 등록·갱신 — 관리자 등록(POST /admins/license)과 같은 처리를 테넌트를 지정해 한다.
     * SYSTEM_MANAGED 프로비저닝용(관리자 경로는 막힘), SELF_MANAGED 도 entry-key 없이 쓸 수 있다.
     */
    public void registerLicense(int tenantSeq, byte[] licenseBlob) {
        tenantFindService.findByTenantSeq(tenantSeq); // 없는 테넌트면 TENANT_NOT_FOUND
        licenseService.registerLicense(tenantSeq, licenseBlob);
    }

    /** 프론트 관리자 진입 라우트 (DriveFront ROUTES.ADMIN_AUTH_ENTRY) */
    private static final String ADMIN_AUTH_ENTRY_PATH = "/admin/auth/entry";

    @Transactional
    public SuperAdminTenantAccessTokenResponse issue(Integer tenantSeq, Integer loginUserSeq) {
        String entryKey = tenantAccessTokenService.issue(loginUserSeq, tenantSeq);
        // 테넌트 주소 체계(서브도메인/경로)는 서버만 알므로 진입 URL 은 여기서 완성해 준다
        String entryUrl = clientDomainService.getDomain(tenantSeq) + ADMIN_AUTH_ENTRY_PATH
                + "?key=" + URLEncoder.encode(entryKey, StandardCharsets.UTF_8);
        return SuperAdminTenantAccessTokenResponse.of(entryKey, entryUrl);
    }
}
