package com.thinkfree.drive.api.v1.admin.smtpconfig.response;

import com.thinkfree.drive.global.time.DriveIsoLocalDateTimeFormat;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class SmtpConfigResponse {
    private Integer smtpConfigSeq;
    private Integer tenantSeq;
    private String clientName;
    private String host;
    private Integer port;
    private EncryptionType encryptionType;
    private AuthType authType;
    private String username;
    /** 비밀번호 저장 여부 — 값은 응답하지 않는다 */
    private boolean hasPassword;
    /** OAuth 토큰 저장 여부 — 값은 응답하지 않는다 */
    private boolean hasOauthInfo;
    private String fromAddress;
    private String testReceiverAddress;
    private Integer registerUserSeq; // 생성자
    @DriveIsoLocalDateTimeFormat
    private Instant registerDate;
    private Integer updateUserSeq;   // 수정자
    @DriveIsoLocalDateTimeFormat
    private Instant updateDate;

    public static SmtpConfigResponse of(SmtpConfigData entity, boolean hasPassword, boolean hasOauthInfo) {
        return SmtpConfigResponse.builder()
                .smtpConfigSeq(entity.getSmtpConfigSeq())
                .tenantSeq(entity.getTenantSeq())
                .clientName(entity.getClientName())
                .host(entity.getHost())
                .port(entity.getPort())
                .encryptionType(entity.getEncryptionType())
                .authType(entity.getAuthType())
                .username(entity.getUsername())
                .hasPassword(hasPassword)
                .hasOauthInfo(hasOauthInfo)
                .fromAddress(entity.getFromAddress())
                .testReceiverAddress(entity.getTestReceiverEmail())
                .registerUserSeq(entity.getRegisterUserSeq())
                .registerDate(entity.getRegisterDate())
                .updateUserSeq(entity.getUpdateUserSeq())
                .updateDate(entity.getUpdateDate())
                .build();

    }
}
