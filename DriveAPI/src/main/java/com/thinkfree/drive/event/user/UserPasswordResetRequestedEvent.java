package com.thinkfree.drive.event.user;

import java.util.Locale;

public record UserPasswordResetRequestedEvent(
        String toEmail,
        String userName,
        String serviceName,
        String token,
        Locale locale,
        Integer tenantSeq
) {
}

