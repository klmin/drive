package com.thinkfree.drive.api.v1.superadmin.tenant.response;

/**
 * 슈퍼어드민 테넌트 등록·수정 모달의 총 용량 입력 검증용 — 화면 진입 시 1회 조회, 입력 판정은 프론트.
 * <ul>
 *   <li>하한: 등록 1, 수정 tenantMinimumQuotaByte</li>
 *   <li>상한: storageAvailByte 가 null 이면 없음. 아니면 기준값 + storageAvailByte
 *       (기준값 = 등록 0, 수정 tenantTotalQuotaByte — null(키 누락)이면 tenantAllocatedQuotaByte)</li>
 *   <li>같은 총 용량을 보내도 관리형 SINGLE의 사용자 할당량 불일치를 복구한다.</li>
 * </ul>
 * 저장 시 서버가 잠금 후 다시 판정한다(STORAGE_CAPACITY_INSUFFICIENT·TENANT_QUOTA_BELOW_ALLOCATED).
 */
public record SuperAdminTenantQuotaCapacityResponse(
        /** 스토리지 전체 용량(byte, 표시용) — 용량 조회 불가(S3·RustFS)면 null */
        Long storageTotalByte,
        /** 스토리지 가용량(byte, 상한 판정 기준) — 용량 조회 불가(S3·RustFS)면 null = 상한 없음 */
        Long storageAvailByte,
        /** 테넌트 총 용량(byte) — tenantSeq 없음(등록) 또는 설정 키 누락이면 null */
        Long tenantTotalQuotaByte,
        /** 테넌트 사용자 할당량 합(byte) — tenantSeq 없음(등록)이면 null */
        Long tenantAllocatedQuotaByte,
        /** 최소 총 용량: 관리형 SINGLE은 실제 사용량(최소 1), 나머지는 사용자 할당량 합(최소 1). */
        Long tenantMinimumQuotaByte
) {
}
