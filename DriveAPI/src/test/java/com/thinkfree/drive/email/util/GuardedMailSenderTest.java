package com.thinkfree.drive.email.util;

import com.thinkfree.drive.httpclient.guard.OutboundAddressGuard;
import com.thinkfree.drive.httpclient.guard.OutboundGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardedMailSenderTest {

    @Test
    void blocksInternalSmtpHostBeforeConnecting() {
        var sender = new MailSenderFactory.GuardedMailSender(
                new OutboundAddressGuard(new OutboundGuardProperties(true, List.of())));
        sender.setHost("127.0.0.1");
        sender.setPort(1);   // 가드가 막지 못하면 연결 거부로 다른 예외가 난다
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("from@example.invalid");
        message.setTo("to@example.invalid");
        message.setText("x");

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(MailSendException.class)
                .satisfies(e -> assertThat(e).hasStackTraceContaining("blocked by outbound guard"));
    }
}
