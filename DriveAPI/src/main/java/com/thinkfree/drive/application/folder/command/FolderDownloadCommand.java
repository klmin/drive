package com.thinkfree.drive.application.folder.command;

public record FolderDownloadCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq
)
{
}
