package com.thinkfree.drive.application.starred.command;

import lombok.Builder;

@Builder
public record StarredSaveCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq
)
{
}
