package com.thinkfree.drive.api.response;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record StreamingDownloadResponse
        (
                ContentDisposition contentDisposition,
                Long contentLength,
                MediaType mediaType,
                StreamingResponseBody body
        ) {
    
    // 빈 응답 리턴 (프로필이미지를 찾을 key가 없는 상태 = 프로필 이미지 없음 상태)
    public static StreamingDownloadResponse empty() {
        return new StreamingDownloadResponse(
                ContentDisposition.attachment().build(), // contentDisposition
                0L, // contentLength
                MediaType.APPLICATION_OCTET_STREAM,
                outputStream -> {
                } // 빈 스트림
        );
    }
}
