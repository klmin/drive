package com.thinkfree.drive.license.auth.request;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증 서버 요청 본문.
 *
 * @param requestedAt DB 서버 시각(epoch millis)을 문자열로 보낸다. 인증 서버가 문자열로 받는다
 * @param cur         현재 사용량. SEAT 만 활성 사용자 수를 보내고 그 외에는 생략한다
 * @param licenseFile 라이선스 소유 증명. 파일에서 "TR" 마커를 뗀 JWS Compact 문자열
 * @param deviceId    설치 식별자. 같은 설치에서는 항상 같은 값이다
 * @param deviceHash  서버 MAC 주소의 SHA-256(Base64). 구하지 못하면 생략한다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LicenseAuthRequest
(
    Long licenseId,
    Long clientId,
    String requestedAt,
    Long cur,
    String licenseFile,
    String deviceId,
    String deviceHash
)
{
}
