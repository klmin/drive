package com.thinkfree.drive.license.data;

import java.time.Instant;

/**
 * 온라인 인증 결과 저장 값.
 * successAt·initAt·signedData 가 null 이면 기존 값을 그대로 둔다.
 */
public record LicenseAuthResultData
(
    int tenantSeq,
    String status,
    int failCount,
    Instant checkedAt,
    Instant successAt,
    Instant initAt,
    String signedData
)
{
}
