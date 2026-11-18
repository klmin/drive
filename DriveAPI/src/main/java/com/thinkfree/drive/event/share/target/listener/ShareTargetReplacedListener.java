package com.thinkfree.drive.event.share.target.listener;

import com.thinkfree.drive.email.service.EmailService;
import com.thinkfree.drive.event.share.target.ShareTargetReplacedEvent;
import com.thinkfree.drive.event.share.target.converter.ShareTargetEventConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShareTargetReplacedListener {
    
    private final ShareTargetEventConverter shareTargetEventConverter;
    private final EmailService emailService;
    
    @TransactionalEventListener
    @Async
    public void onShareTargetReplaced(ShareTargetReplacedEvent event) {
        log.info("## onShareTargetReplaced ##");
        log.info("ShareTargetReplacedEvent : {}", event);
        
        var shareTargetAddedDto = shareTargetEventConverter.toShareTargetAddedEmailDto(event);
        emailService.sendShareTargetAddedEmail(shareTargetAddedDto);
    }
}
