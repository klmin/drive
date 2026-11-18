package com.thinkfree.drive.application.resource.command;

public record ResourceFindCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq,
    Boolean isDelete
)
{
}
