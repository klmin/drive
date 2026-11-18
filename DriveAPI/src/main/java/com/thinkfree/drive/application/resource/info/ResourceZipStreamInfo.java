package com.thinkfree.drive.application.resource.info;

import java.io.IOException;
import java.io.OutputStream;

public record ResourceZipStreamInfo
(
    String zipFileName,
    Writer writer
)
{
    /** 전송 프레임워크 중립 스트림 작성자 — 어댑터가 StreamingResponseBody 등으로 감싼다. */
    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream outputStream) throws IOException;
    }
}
