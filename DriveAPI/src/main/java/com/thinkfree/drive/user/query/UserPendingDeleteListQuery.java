package com.thinkfree.drive.user.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class UserPendingDeleteListQuery {
    private Instant pendingDeleteStartDate;
    private Instant pendingDeleteEndDate;
    private Instant deleteScheduleStartDate;
    private Instant deleteScheduleEndDate;
    private Integer tenantSeq;
    private CursorPaginationQuery page;
}
