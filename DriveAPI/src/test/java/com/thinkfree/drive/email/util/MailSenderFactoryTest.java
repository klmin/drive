package com.thinkfree.drive.email.util;

import com.thinkfree.drive.httpclient.guard.OutboundAddressGuard;
import com.thinkfree.drive.httpclient.guard.OutboundGuardProperties;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import com.thinkfree.drive.smtpconfig.util.SmtpPasswordEncryptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailSenderFactoryTest {

    private final SmtpPasswordEncryptor encryptor = mock(SmtpPasswordEncryptor.class);
    private final MailSenderFactory factory = new MailSenderFactory(encryptor,
            new OutboundAddressGuard(new OutboundGuardProperties(false, List.of())));

    private Properties props(EncryptionType type) {
        when(encryptor.decrypt(anyString())).thenReturn("pw");
        var config = new SmtpConfigData(1, 1, "c", "smtp.example.invalid", 587, type, AuthType.IDPW, "u",
                new SmtpPassword("enc"), null, "from@example.invalid", "to@example.invalid", 1, null, null, null);
        return factory.createDynamicMailSender(config).getJavaMailProperties();
    }

    @Test
    void tlsRequiresStartTlsAndChecksServerIdentity() {
        Properties p = props(EncryptionType.TLS);
        assertThat(p.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(p.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(p.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
    }

    @Test
    void sslChecksServerIdentity() {
        Properties p = props(EncryptionType.SSL);
        assertThat(p.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(p.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
    }

    @Test
    void noneStaysPlain() {
        Properties p = props(EncryptionType.NONE);
        assertThat(p.getProperty("mail.smtp.starttls.required")).isNull();
        assertThat(p.getProperty("mail.smtp.ssl.enable")).isNull();
    }
}
