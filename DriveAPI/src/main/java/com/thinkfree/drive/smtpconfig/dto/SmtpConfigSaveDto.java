package com.thinkfree.drive.smtpconfig.dto;

import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;

/** SMTP 설정 등록·수정 service 입력 — SmtpConfigService.saveOrUpdateSmtpConfig(smtpConfigSeq 가 null 이면 등록) */
public record SmtpConfigSaveDto
(
    Integer smtpConfigSeq,
    String clientName,
    String host,
    Integer port,
    EncryptionType encryptionType,
    AuthType authType,
    String username,
    String encPwd,
    String oauthInfo,
    String fromAddress,
    String testReceiverAddress
)
{
}
