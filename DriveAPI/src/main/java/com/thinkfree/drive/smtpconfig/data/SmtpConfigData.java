package com.thinkfree.drive.smtpconfig.data;

import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** SMTP 설정 전 컬럼 — 읽기 전용 값(관리자 응답·메일 발송). getter 이름은 엔티티 {@code SmtpConfig} 와 같다. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(SmtpConfigJpaRepository#SELECT_DATA)
public class SmtpConfigData {

    private Integer smtpConfigSeq;
    private Integer tenantSeq;
    private String clientName;
    private String host;
    private Integer port;
    private EncryptionType encryptionType;
    private AuthType authType;
    private String username;
    private SmtpPassword encPwd;         // 암호화 값(SmtpPasswordConverter 적용 후)
    private String oauthInfo;
    private String fromAddress;
    private String testReceiverEmail;
    private Integer registerUserSeq;
    private Instant registerDate;
    private Integer updateUserSeq;
    private Instant updateDate;
}
