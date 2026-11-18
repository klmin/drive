package com.thinkfree.drive.user.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
public class UserDeleteMarkQuery {
    private List<Integer> userSeqList;
    private Instant deleteDate;
    private Integer updateUserSeq;
    private Instant updateDate;
    private boolean requirePendingDelete;
}
