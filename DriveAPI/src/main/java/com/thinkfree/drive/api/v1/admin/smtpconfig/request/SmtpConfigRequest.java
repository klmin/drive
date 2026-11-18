package com.thinkfree.drive.api.v1.admin.smtpconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SmtpConfigRequest {

    @Schema(title = "SMTP configuration ID", description = "ID of the configuration to update. Leave unset when registering a new configuration.")
    private Integer smtpConfigSeq; // smtp 설정 seq (없다면 새로 등록)

    @NotBlank
    @Schema(title = "SMTP configuration name", description = "Customer or organization name identifying this SMTP configuration.")
    private String clientName; // 고객사명
    @NotBlank
    @Schema(title = "SMTP host", description = "Hostname of the mail server that sends Drive emails.")
    private String host;           // SMTP 서버 주소
    @NotNull
    @Schema(title = "SMTP port", description = "Port number accepted by the mail server for the selected encryption mode.")
    private Integer port;              // SMTP 포트 번호
    @NotNull
    @Schema(title = "SMTP encryption", description = "Select SSL, TLS or NONE to match the mail server.")
    private EncryptionType encryptionType; // 보안 방식 (SSL, TLS, NONE)
    @NotNull
    @Schema(title = "SMTP authentication", description = "IDPW uses a username and password; OAUTH uses an access token.")
    private AuthType authType;       // SMTP 인증 방식 (ID/PW, OAuth)
    @NotBlank
    @Schema(title = "SMTP username", description = "Mail-server account used to authenticate outgoing messages.")
    private String username;       // SMTP 사용자명 (로그인 계정)
    
    @Schema(title = "SMTP password", description = "Mail-server password used when authType is IDPW.")
    private String encPwd;       // SMTP 비밀번호 (ID/PW 사용 시 필수)

    @Schema(title = "SMTP OAuth access token", description = "Access token used for the mail account when authType is OAUTH.")
    private String oauthInfo;      // OAuth 정보 (선택)

    @NotBlank
    @Schema(title = "Sender email", description = "Email address shown as the sender of Drive messages.")
    private String fromAddress;    // 발신자 이메일
    @NotBlank
    @Schema(title = "Test recipient email", description = "Email address that receives the SMTP test message.")
    private String testReceiverAddress; // 테스트 메일 수신자 (테스트용)
}

