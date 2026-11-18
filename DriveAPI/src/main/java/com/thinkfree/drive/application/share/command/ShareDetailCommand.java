package com.thinkfree.drive.application.share.command;

public record ShareDetailCommand
(
    Long shareSeq,
    Integer sharedByUserSeq
)
{
}
