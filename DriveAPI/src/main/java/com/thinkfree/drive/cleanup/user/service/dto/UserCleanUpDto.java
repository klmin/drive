package com.thinkfree.drive.cleanup.user.service.dto;

import java.time.Instant;
import java.util.List;

public record UserCleanUpDto
(
    List<Integer> userSeqList,
    Instant deleteDueAt,
    int actorUserSeq
)
{
}
