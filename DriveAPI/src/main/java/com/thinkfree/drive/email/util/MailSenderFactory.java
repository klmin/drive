package com.thinkfree.drive.email.util;

import com.thinkfree.drive.smtpconfig.auth.OAuth2Authenticator;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.util.SmtpPasswordEncryptor;
import jakarta.mail.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
public class MailSenderFactory {

    private final SmtpPasswordEncryptor smtpPasswordEncryptor;

    /**
     * JavaMailSender 동적 생성
     */
    public JavaMailSenderImpl createDynamicMailSender(SmtpConfigData smtpConfig) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpConfig.getHost());
        mailSender.setPort(smtpConfig.getPort());
        mailSender.setUsername(smtpConfig.getUsername());
        mailSender.setPassword(smtpPasswordEncryptor.decrypt(smtpConfig.getEncPwd().getValue()));

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "false");

        if ("TLS".equalsIgnoreCase(smtpConfig.getEncryptionType().getValue())) {
            props.put("mail.smtp.starttls.enable", "true");
        } else if ("SSL".equalsIgnoreCase(smtpConfig.getEncryptionType().getValue())) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        // 인증 방식 분기
        if (AuthType.IDPW.equals(smtpConfig.getAuthType())) {
            mailSender.setUsername(smtpConfig.getUsername());
            mailSender.setPassword(smtpPasswordEncryptor.decrypt(smtpConfig.getEncPwd().getValue()).trim());
        } else if (AuthType.OAUTH.equals(smtpConfig.getAuthType())) {
            // OAUTH 설정 (구글이나 마이크로소프트 연동 시)
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
            props.put("mail.smtp.auth.login.disable", "true");
            props.put("mail.smtp.auth.plain.disable", "true");

            // OAUTH2 토큰은 smtpConfig.getOauthInfo() 에 저장된 값 사용
            // 실제 OAUTH2 인증은 별도의 Authenticator 구현 필요
            String accessToken = smtpConfig.getOauthInfo();

            Session session = Session.getInstance(props, new OAuth2Authenticator(smtpConfig.getUsername(), accessToken));
            mailSender.setSession(session);

        } else {
            throw new IllegalArgumentException("지원하지 않는 인증 방식입니다.");
        }

        return mailSender;
    }
}

