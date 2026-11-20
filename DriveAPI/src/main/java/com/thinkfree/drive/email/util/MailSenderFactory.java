package com.thinkfree.drive.email.util;

import com.thinkfree.drive.httpclient.guard.OutboundAddressGuard;
import com.thinkfree.drive.smtpconfig.auth.OAuth2Authenticator;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.util.SmtpPasswordEncryptor;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.net.UnknownHostException;
import java.util.Properties;

@Component
@RequiredArgsConstructor
public class MailSenderFactory {

    private final SmtpPasswordEncryptor smtpPasswordEncryptor;
    private final OutboundAddressGuard outboundAddressGuard;

    /**
     * JavaMailSender 동적 생성
     */
    public JavaMailSenderImpl createDynamicMailSender(SmtpConfigData smtpConfig) {
        JavaMailSenderImpl mailSender = new GuardedMailSender(outboundAddressGuard);
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
            // STARTTLS 를 못 하면 평문으로 진행하지 않고 실패 — 중간자가 STARTTLS 광고를 지워 계정·비밀번호가 평문으로 가는 것 방지
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        } else if ("SSL".equalsIgnoreCase(smtpConfig.getEncryptionType().getValue())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");   // 인증서의 호스트 이름 검증
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

    /**
     * SMTP host 는 테넌트 관리자 입력 — 연결 직전에 내부망 가드({@code drive.outbound-guard})로 검사한다.
     * 차단이면 MessagingException(→ 호출부의 기존 발송 실패 처리). 조회 실패(없는 호스트)는 기존처럼 JavaMail 연결 단계에서 실패하게 둔다.
     * JavaMail 이 연결할 때 DNS 를 다시 조회하므로 HTTP 가드와 달리 검사·연결 사이 DNS 바꿔치기까지는 막지 못한다.
     */
    static class GuardedMailSender extends JavaMailSenderImpl {

        private final OutboundAddressGuard guard;

        GuardedMailSender(OutboundAddressGuard guard) {
            this.guard = guard;
        }

        @Override
        protected Transport connectTransport() throws MessagingException {
            String host = getHost();
            if (host != null) {
                try {
                    guard.resolveAndVerify(host);
                } catch (OutboundAddressGuard.BlockedAddressException e) {
                    throw new MessagingException("SMTP host blocked by outbound guard: " + host, e);
                } catch (UnknownHostException ignored) {
                    // 없는 호스트 — 기존 동작(연결 실패)에 맡긴다
                }
            }
            return super.connectTransport();
        }
    }
}
