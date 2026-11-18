package com.thinkfree.drive.email.service;

import com.thinkfree.drive.email.dto.ShareTargetAddedEmailDto;
import com.thinkfree.drive.email.template.service.EmailTemplateFindService;
import com.thinkfree.drive.email.util.MailSenderFactory;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.service.SmtpConfigFindService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 공유 대상 등록 메일 — 같은 이메일이 여러 테넌트에 있을 때(테넌트별 user_id 유니크) 수신자 분류·중복 제거 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService.sendShareTargetAddedEmail — 테넌트 분류·수신자 중복 제거")
class EmailServiceShareTargetTest {

    private static final int ACTOR_TENANT = 1;
    private static final int OTHER_TENANT = 2;

    @Mock
    private SmtpConfigFindService smtpConfigFindService;
    @Mock
    private MailSenderFactory mailSenderFactory;
    @Mock
    private MessageSourceService messageSourceService;
    @Mock
    private EmailTemplateFindService emailTemplateFindService;
    @Mock
    private TenantFindService tenantFindService;
    @Mock
    private UserFindService userFindService;
    @Mock
    private JavaMailSenderImpl mailSender;

    @InjectMocks
    private EmailService emailService;

    private static UserData user(String userId, int tenantSeq) {
        return UserData.builder().userId(userId).tenantSeq(tenantSeq).build();
    }

    private static ShareTargetAddedEmailDto dto(List<String> recipients) {
        return ShareTargetAddedEmailDto.builder()
                .tenantSeq(ACTOR_TENANT).requestUserId("actor@test.com").requestUserName("보낸이")
                .resourceType(ResourceType.FILE).resourceName("a.docx").recipientEmails(recipients).locale(Locale.KOREAN)
                .build();
    }

    private List<SimpleMailMessage> sent(int count) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(count)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("보낸 사람 테넌트에도 있는 이메일은 같은 테넌트, 다른 테넌트에만 있거나 계정이 없으면 다른 테넌트 — 같은 주소는 1통")
    void classifyAndDistinct() {
        given(smtpConfigFindService.findCurrentSmtpConfig(ACTOR_TENANT)).willReturn(Mockito.mock(SmtpConfigData.class));
        given(mailSenderFactory.createDynamicMailSender(any())).willReturn(mailSender);
        given(userFindService.findUserTenantsByUserIds(List.of("both@test.com", "other@test.com", "none@test.com")))
                .willReturn(List.of(user("both@test.com", OTHER_TENANT), user("both@test.com", ACTOR_TENANT), user("other@test.com", OTHER_TENANT)));
        given(tenantFindService.findByTenantSeq(ACTOR_TENANT)).willReturn(TenantData.builder().tenantSeq(ACTOR_TENANT).tenantName("본사").build());

        emailService.sendShareTargetAddedEmail(dto(List.of("both@test.com", "other@test.com", "both@test.com", "none@test.com")));

        List<SimpleMailMessage> messages = sent(2);
        assertThat(messages.get(0).getTo()).as("같은 테넌트 — 테넌트명 미표기 묶음").containsExactly("both@test.com");
        assertThat(messages.get(1).getTo()).as("다른 테넌트 묶음").containsExactly("other@test.com", "none@test.com");
    }

    @Test
    @DisplayName("수신자가 모두 같은 테넌트면 테넌트명 조회 없이 1통")
    void allSameTenant() {
        given(smtpConfigFindService.findCurrentSmtpConfig(ACTOR_TENANT)).willReturn(Mockito.mock(SmtpConfigData.class));
        given(mailSenderFactory.createDynamicMailSender(any())).willReturn(mailSender);
        given(userFindService.findUserTenantsByUserIds(List.of("a@test.com")))
                .willReturn(List.of(user("a@test.com", OTHER_TENANT), user("a@test.com", ACTOR_TENANT)));

        emailService.sendShareTargetAddedEmail(dto(List.of("a@test.com", "a@test.com")));

        assertThat(sent(1).get(0).getTo()).containsExactly("a@test.com");
        verify(tenantFindService, never()).findByTenantSeq(ACTOR_TENANT);
    }
}
