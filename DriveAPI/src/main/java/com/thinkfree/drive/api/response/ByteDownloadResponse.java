package com.thinkfree.drive.api.response;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

public record ByteDownloadResponse
(
        ContentDisposition contentDisposition,
        Long contentLength,
        MediaType mediaType,
        byte[] body
)
{
}
