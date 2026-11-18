package com.thinkfree.drive.user.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UserBulkStatusQuery {
    private List<String> userIds;
    private Integer tenantSeq;
}
