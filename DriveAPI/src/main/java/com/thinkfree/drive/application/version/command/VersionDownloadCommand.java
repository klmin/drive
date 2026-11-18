package com.thinkfree.drive.application.version.command;

public record VersionDownloadCommand
(
    Long resourceSeq,
    Long resourceVersionSeq,
    Integer sharedByUserSeq
)
{
}
