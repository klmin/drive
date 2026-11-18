package com.thinkfree.drive.user.dto;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 프로필 이미지 다운로드 결과 — 전송 중립(파일 이름·크기·타입·스트림 작성자).
 * ContentDisposition·MediaType 조립과 StreamingResponseBody 래핑은 어댑터(UserController) 몫.
 * {@link #empty()} = 보여줄 이미지 없음(파일 이름 없음·0바이트·octet-stream).
 */
public record ProfileImageResult
(
    String fileName,
    Long contentLength,
    String contentType,
    Writer writer
)
{
    private static final String OCTET_STREAM = "application/octet-stream";

    /** 전송 프레임워크 중립 스트림 작성자 */
    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream outputStream) throws IOException;
    }

    public static ProfileImageResult empty() {
        return new ProfileImageResult(null, 0L, OCTET_STREAM, outputStream -> {
        });
    }

    public boolean isEmpty() {
        return fileName == null;
    }
}
