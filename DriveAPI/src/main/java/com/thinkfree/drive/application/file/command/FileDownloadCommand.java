package com.thinkfree.drive.application.file.command;

public record FileDownloadCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq
)
{
}
