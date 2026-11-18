package com.thinkfree.drive.common.file.model;

import java.io.InputStream;

public record FileData
(
        String contentType,
        InputStream content,
        long contentLength
)
{
}
