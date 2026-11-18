package com.thinkfree.drive.application.file.command;

public record FileReadCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq,
    Integer offset
)
{
}
