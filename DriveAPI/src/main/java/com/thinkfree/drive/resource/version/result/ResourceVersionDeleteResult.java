package com.thinkfree.drive.resource.version.result;

public record ResourceVersionDeleteResult
(
        Long resourceVersionSeq,
        String resourceSaveName,
        Long sizeByte,
        Integer versionNumber
)
{
}
