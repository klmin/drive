package com.thinkfree.drive.smtpconfig.result;

import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;

/** 현재 SMTP 설정 조회 결과 — 설정 행과 비밀번호·OAuth 토큰 저장 여부(값은 내보내지 않음) */
public record SmtpConfigDetailResult
(
    SmtpConfigData smtpConfig,
    boolean hasPassword,
    boolean hasOauthInfo
)
{
}
