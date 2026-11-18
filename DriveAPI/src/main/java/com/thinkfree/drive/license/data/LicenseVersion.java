package com.thinkfree.drive.license.data;

import java.time.Instant;

/**
 * 캐시가 최신인지 판단하는 값.
 * 파일이 바뀐 시각(updatedAt)과 온라인 인증 결과가 바뀐 시각(authCheckedAt)은 서로 다른 컬럼이라 둘 다 본다.
 */
public record LicenseVersion
(
    Instant updatedAt,
    Instant authCheckedAt
)
{
}
