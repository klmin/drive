package com.thinkfree.drive.resource.data;

public record ResourceUsageSummaryData
(
    long totalBytes,
    long liveBytes,
    long trashBytes
)
{
}
