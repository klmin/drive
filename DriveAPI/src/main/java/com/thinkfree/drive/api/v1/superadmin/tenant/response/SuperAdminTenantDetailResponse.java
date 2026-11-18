package com.thinkfree.drive.api.v1.superadmin.tenant.response;

import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;

import java.util.List;

/**
 * 슈퍼어드민 테넌트 단건 조회 응답 — 수정 모달 prefill 용.
 * 편집 단위는 서브도메인이므로 풀 domain 은 제외(목록 응답 전용)하고, subdomain 과
 * 소속 베이스 도메인(baseDomainSeq)만 내려준다. base 도메인 문자열은 별도 API 로 조회.
 */
public record SuperAdminTenantDetailResponse(
        Integer tenantSeq,
        String tenantName,
        String subdomain,
        Integer baseDomainSeq,
        /** 테넌트 유형·사용자 모드 — 생성 후 변경 불가 */
        TenantType tenantType,
        TenantUserMode userMode,
        /** ADMIN 허용 IP — SYSTEM_MANAGED 는 쓰지 않음(빈 목록) */
        List<String> allowedIps,
        /** 테넌트 총 용량(byte) */
        Long totalQuotaByte
) {
}
