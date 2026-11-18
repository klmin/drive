package com.thinkfree.drive.api.v1.admin.systemconfig.response;

/**
 * 테넌트 총 용량과 사용자 할당량 합계(byte).
 * 할당 가능량 = totalQuotaByte - allocatedQuotaByte (사용자 등록·할당량 증가 판정 기준).
 * totalQuotaByte 는 설정 키가 없으면 null.
 */
public record StorageQuotaResponse(
        Long totalQuotaByte,
        long allocatedQuotaByte
) {
}
