package com.thinkfree.drive.license.data;

import java.time.Instant;

/**
 * 라이선스 파일과 저장된 온라인 인증 결과.
 * 오프라인 라이선스는 authStatus 가 null 이다.
 * updatedAt 은 파일이 저장된 시각이다. 인증 결과를 기록할 때 그 사이 파일이 바뀌었는지 보는 데 쓴다.
 */
public record LicenseAuthStateData
(
    byte[] data,
    String authStatus,
    Integer authFailCount,
    Instant authCheckedAt,
    Instant authInitAt,
    Instant authSuccessAt,
    String authSignedData,
    Instant updatedAt
)
{
}
