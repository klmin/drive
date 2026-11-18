package com.thinkfree.drive.application.file.command;

import lombok.Builder;

@Builder
public record FileCopyCommand
(
    Integer sharedByUserSeq,
    Integer targetUserSeq,
    Long parentFolderSeq
)
{
}
