package com.thinkfree.drive.event.user.listener;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.email.service.EmailService;
import com.thinkfree.drive.event.user.UserCsvRegisteredEvent;
import com.thinkfree.drive.event.user.converter.UserEventConverter;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsersCsvRegisteredListener {

    private final UserEventConverter userEventConverter;
    private final EmailService emailService;
    private final ClientDomainService clientDomainService;
    private final AppFrontProperty appFrontProperty;

    @TransactionalEventListener
    @Async
    public void onUsersCsvRegistered(UserCsvRegisteredEvent event) {
        log.info("## onUsersCsvRegistered ##");
        log.info("userCsvRegisteredEvent - tenantSeq : {}, users : {}명", event.tenantSeq(), event.users().size());   // 이벤트에 초기 비밀번호가 있어 통째로 남기지 않는다

        var baseUrl = clientDomainService.getDomain(event.tenantSeq());
        var loginUrl = StringUtil.buildUrl(baseUrl + appFrontProperty.loginPath());
        var serviceName = event.serviceName();
        var userCsvRegisteredEmailDtoList = event.users()
                                                 .stream()
                                                 .map(d -> userEventConverter.toAdminUserRegisteredEmailDto(d.userEmail(), baseUrl, d.pw(), loginUrl, serviceName))
                                                 .toList();
        
        emailService.sendUserCsvRegisteredEmail(event.tenantSeq(), userCsvRegisteredEmailDtoList, event.locale());
      
    }
}
