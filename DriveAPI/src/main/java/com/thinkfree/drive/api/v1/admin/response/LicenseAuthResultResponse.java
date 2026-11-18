package com.thinkfree.drive.api.v1.admin.response;

/**
 * 수동 인증 결과. 인증이 실패해도 요청 자체는 성공이므로 결과를 담아 돌려준다.
 *
 * @param authStatus SUCCESS / GRACE_PERIOD / EXPIRED / NOT_STARTED / INACTIVE / AUTH_EXPIRED
 * @param lastAuthAt 이번 인증이 성공했을 때 그 시각 (yyyy-MM-dd HH:mm:ss). 실패면 null
 * @param errorCode  인증 서버의 실패 코드. 성공이거나 연결 실패면 null
 */
public record LicenseAuthResultResponse
(
    String authStatus,
    String lastAuthAt,
    String errorCode
)
{
}
