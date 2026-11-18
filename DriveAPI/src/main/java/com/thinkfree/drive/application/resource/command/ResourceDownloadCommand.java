package com.thinkfree.drive.application.resource.command;

import java.util.List;

public record ResourceDownloadCommand
(
    List<DownloadItem> items
)
{
    public record DownloadItem(
            Integer sharedByUserSeq,
            Long resourceSeq
    )
    {
    }
}
