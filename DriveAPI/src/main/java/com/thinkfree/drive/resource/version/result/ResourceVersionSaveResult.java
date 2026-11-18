package com.thinkfree.drive.resource.version.result;

import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;

public record ResourceVersionSaveResult
(
    Long resourceVersionSeq,
    String resourceSaveName,
    Boolean isLimitExceeded,
    ResourceVersionRowData rollbackData
)
{
}
