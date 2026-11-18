package com.thinkfree.drive.license.auth.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 인증 서버 응답 중 Drive 가 쓰는 값.
 *
 * @param status       SUCCESS / FAIL
 * @param errorCode    실패 코드 (숫자 문자열)
 * @param initTime     최초 INIT 성공 시각 (epoch millis 문자열)
 * @param lastAuthTime 마지막 인증 성공 시각 (epoch millis)
 * @param signedData   인증 서버가 서명한 원장 정보 (JWT)
 * @param serverKid    signedData 를 검증할 공개키 ID. JWT 헤더에는 kid 가 없다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LicenseAuthResponse
(
    String status,
    String errorCode,
    String message,
    String initTime,
    Long lastAuthTime,
    String signedData,
    String serverKid
)
{
    public static final String STATUS_SUCCESS = "SUCCESS";

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }
}
