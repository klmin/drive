package com.thinkfree.drive.notification.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationListQuery {
    private Integer userSeq;
    private Boolean isRead;
    private CursorPaginationQuery page;
}
