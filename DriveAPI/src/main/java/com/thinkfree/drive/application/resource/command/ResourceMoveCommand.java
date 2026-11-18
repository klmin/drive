package com.thinkfree.drive.application.resource.command;

public record ResourceMoveCommand
(
    Integer sharedByUserSeq,
    Integer targetUserSeq,
    Long parentFolderSeq
)
{
}
