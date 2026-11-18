package com.thinkfree.drive.common.file.cache;

public record CachedFile
(
    byte[] bytes,
    String contentType,
    long length
)
{
}
