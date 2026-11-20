package com.thinkfree.drive.event.user.listener;

import com.thinkfree.drive.email.service.EmailService;
import com.thinkfree.drive.event.user.UserPasswordResetRequestedEvent;
import com.thinkfree.drive.event.user.converter.UserPasswordResetEventConverter;
import com.thinkfree.drive.global.service.ClientDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserPasswordResetListener {

    private final UserPasswordResetEventConverter userPasswordResetEventConverter;
    private final EmailService emailService;
    private final ClientDomainService clientDomainService;

    @TransactionalEventListener
    @Async
    public void onUserPasswordResetRequested(UserPasswordResetRequestedEvent event) {
        log.info("## onUserPasswordResetRequested ##");
        log.info("UserPasswordResetRequestedEvent - tenantSeq : {}", event.tenantSeq());   // 이벤트에 재설정 토큰이 있어 통째로 남기지 않는다

        var domainUrl = clientDomainService.getDomain(event.tenantSeq());

        var resetEmailDto = userPasswordResetEventConverter.toUserPasswordResetEmailDto(event, domainUrl, event.locale());
        emailService.sendPasswordResetEmail(resetEmailDto);
    }
}
