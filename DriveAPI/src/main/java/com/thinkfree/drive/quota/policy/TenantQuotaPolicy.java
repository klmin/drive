package com.thinkfree.drive.quota.policy;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 테넌트 총 용량 × 사용자 할당량 <b>순수 정책</b> — 값만 받아 판정한다(주입·DB 접근 없음, 단위테스트 대상).
 * 할당량 기준: 사용자 할당량 합 ≤ 테넌트 총 용량, 무제한(-1) 없음.
 * 잠금·조회·저장·스토리지(Ceph) 가용량 검사는 호출부(UseCase)가 한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantQuotaPolicy {

    /** 사용자(USER) 할당량 — 0 보다 커야 한다(무제한 -1 없음). 요청 검증을 거치지 않는 경로(MCP·CSV)도 같은 기준 */
    public static void validateUserQuota(long totalQuotaByte) {
        if (totalQuotaByte <= 0) {
            throw ApiRuntimeException.type(QuotaErrorCode.INVALID_USER_QUOTA);
        }
    }

    /**
     * 보유 역할별 할당량 확정 — 등록·수정 요청의 할당량(생략 가능)을 저장할 값으로 바꾼다.
     * <ul>
     *   <li>USER 보유: 필수(null 이면 REQUIRED_FIELD), 0 보다 커야 한다(0 이하는 INVALID_USER_QUOTA)</li>
     *   <li>USER 미보유(ADMIN 만): 생략(null)·0 만 허용, 0 으로 확정. 그 외 값은 ADMIN_QUOTA_NOT_ALLOWED</li>
     * </ul>
     * 역할 자체의 허용 여부(USER/ADMIN, 1개 이상)는 호출부가 먼저 판정한다.
     */
    public static long resolveUserQuota(Set<RoleType> roles, Long totalQuotaByte) {
        if (!roles.contains(RoleType.USER)) {
            if (totalQuotaByte != null && totalQuotaByte != 0L) {
                throw ApiRuntimeException.type(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);
            }
            return 0L;
        }
        if (totalQuotaByte == null) {
            throw ApiRuntimeException.type(UserErrorCode.REQUIRED_FIELD);
        }
        validateUserQuota(totalQuotaByte);
        return totalQuotaByte;
    }

    /** 사용자 할당량 배정(등록·증가) 가능 여부 — 증감분 0 이하(감소·동일)는 항상 가능 */
    public static boolean isAssignable(long tenantTotalQuotaByte, long allocatedQuotaByte, long allocationDeltaByte) {
        if (allocationDeltaByte <= 0) {
            return true;
        }
        // 합을 만들지 않고 남은 용량과 비교 — 큰 증감분(Long.MAX_VALUE 등)의 오버플로로 통과하지 않게
        return allocationDeltaByte <= tenantTotalQuotaByte - allocatedQuotaByte;
    }

    /**
     * 할당량 합산 — long(DB bigint) 범위를 넘으면 TENANT_QUOTA_EXCEEDED.
     * 일괄·CSV 누적 합이 음수로 돌아 테넌트 총 용량·스토리지 가용량 검사를 통과하지 않게. 넘친 합은 어떤 총 용량보다도 크므로 총 용량 초과로 본다.
     */
    public static long addQuota(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw ApiRuntimeException.type(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        }
    }

    /** 테넌트 총 용량 변경이 필요 없는지 — 키가 없는(current == null) 테넌트는 항상 설정이 필요하다 */
    public static boolean isUnchanged(Long currentQuotaByte, long requestedQuotaByte) {
        return currentQuotaByte != null && currentQuotaByte == requestedQuotaByte;
    }

    /** 테넌트 총 용량은 사용자 할당량 합보다 작게 할 수 없다(같은 값은 허용) */
    public static void requireNotBelowAllocated(long requestedQuotaByte, long allocatedQuotaByte) {
        if (requestedQuotaByte < allocatedQuotaByte) {
            throw ApiRuntimeException.type(QuotaErrorCode.TENANT_QUOTA_BELOW_ALLOCATED);
        }
    }

    /**
     * 테넌트 총 용량 변경의 증가분(음수면 하향) — 스토리지 가용량 검사 대상.
     * 키가 없는 테넌트(처음 설정해 복구)는 이미 사용자에게 나간 할당량 합을 기준으로 잡는다.
     */
    public static long increaseOf(Long currentQuotaByte, long requestedQuotaByte, long allocatedQuotaByte) {
        long baselineQuotaByte = currentQuotaByte != null ? currentQuotaByte : allocatedQuotaByte;
        return requestedQuotaByte - baselineQuotaByte;
    }
}
