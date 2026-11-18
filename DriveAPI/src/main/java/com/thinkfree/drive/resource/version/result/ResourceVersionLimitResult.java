package com.thinkfree.drive.resource.version.result;

import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;

public record ResourceVersionLimitResult
(
        boolean isLimitExceeded,
        ResourceVersionRowData deletedVersion
)
{
}
