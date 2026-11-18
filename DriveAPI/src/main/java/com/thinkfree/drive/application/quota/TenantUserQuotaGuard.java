package com.thinkfree.drive.application.quota;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.policy.TenantQuotaPolicy;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 할당량 배정(등록·증가) × 테넌트 총 용량 — 여러 도메인(tenant 잠금·system_config 총 용량·user 할당 합)을 묶는 흐름이라
 * 도메인 서비스가 아닌 application 계층에 둔다. 판정 자체는 {@link TenantQuotaPolicy}(순수).
 * <p>
 * 테넌트 행 잠금은 호출 트랜잭션 끝(사용자 INSERT/UPDATE 커밋)까지 유지돼야 하므로 MANDATORY.
 * 잠금 순서는 테넌트 → 사용자(PERSEAT 좌석 잠금과 같음) — 사용자 행을 잠그는 흐름은 {@link #lockTenant} 를 먼저 부른다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TenantUserQuotaGuard {

    private final TenantService tenantService;
    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;

    /** 테넌트 행 잠금 — 사용자 행 잠금보다 먼저. 잠근 뒤엔 {@link #ensureAssignableLocked} 로 판정한다 */
    public void lockTenant(int tenantSeq) {
        tenantService.lock(tenantSeq);
    }

    /**
     * 아직 테넌트를 잠그지 않은 흐름(단건 등록·SSO 가입)용 — 증감분이 있으면 잠그고 판정한다.
     * "현재 사용자 할당량 합 + 증감분 ≤ 테넌트 총 용량" 아니면 TENANT_QUOTA_EXCEEDED. 증감분 0 이하는 잠금·조회 없이 통과
     */
    public void ensureAssignable(int tenantSeq, long allocationDeltaByte) {
        if (!isAssignable(tenantSeq, allocationDeltaByte)) {
            throw ApiRuntimeException.type(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        }
    }

    /** {@link #ensureAssignable} 의 판정만 — 실패를 예외 대신 결과로 돌려주는 경로(CSV 실패 파일)용 */
    public boolean isAssignable(int tenantSeq, long allocationDeltaByte) {
        if (allocationDeltaByte <= 0) {
            return true;
        }
        tenantService.lock(tenantSeq);
        return judge(tenantSeq, allocationDeltaByte);
    }

    /** 이미 {@link #lockTenant} 로 잠근 흐름(관리자 수정·일괄)용 — 다시 잠그지 않고 판정만 */
    public void ensureAssignableLocked(int tenantSeq, long allocationDeltaByte) {
        if (allocationDeltaByte > 0 && !judge(tenantSeq, allocationDeltaByte)) {
            throw ApiRuntimeException.type(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        }
    }

    private boolean judge(int tenantSeq, long allocationDeltaByte) {
        long tenantTotalQuotaByte = systemConfigFindService.findTenantTotalQuotaByteOrThrow(tenantSeq); // 키 없으면 데이터 오류 — 기본값으로 대신하지 않음
        long allocatedQuotaByte = userFindService.sumTotalQuotaByteByTenantSeq(tenantSeq);
        return TenantQuotaPolicy.isAssignable(tenantTotalQuotaByte, allocatedQuotaByte, allocationDeltaByte);
    }
}
