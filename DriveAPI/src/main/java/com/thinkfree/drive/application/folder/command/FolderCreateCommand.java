package com.thinkfree.drive.application.folder.command;

import lombok.Builder;

@Builder
public record FolderCreateCommand
(
    Integer sharedByUserSeq,
    Long parentFolderSeq,
    String folderName
)
{
}
