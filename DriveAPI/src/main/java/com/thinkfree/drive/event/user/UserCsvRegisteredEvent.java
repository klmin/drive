package com.thinkfree.drive.event.user;

import java.util.List;
import java.util.Locale;

public record UserCsvRegisteredEvent
(
    Locale locale,
    List<RegisteredUser> users,
    String serviceName,
    int tenantSeq
)
{
    public record RegisteredUser
    (
            String userEmail,
            String pw
    )
    {
    
    }
}
