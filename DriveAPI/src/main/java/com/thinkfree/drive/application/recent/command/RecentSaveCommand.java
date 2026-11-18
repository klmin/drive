package com.thinkfree.drive.application.recent.command;

public record RecentSaveCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq
)
{
}
