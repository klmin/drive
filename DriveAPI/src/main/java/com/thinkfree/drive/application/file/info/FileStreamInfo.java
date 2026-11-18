package com.thinkfree.drive.application.file.info;

import com.thinkfree.drive.application.resource.info.ResourceZipStreamInfo;

/**
 * 단일 파일 스트림의 전송 중립 표현 — ContentDisposition·MediaType 조립과 StreamingResponseBody 래핑은 어댑터 몫.
 * Writer는 application.resource 소유의 함수형 타입을 공용한다 (같은 계층의 값 객체 재사용 — usecase 간 호출 아님).
 */
public record FileStreamInfo
(
    String fileName,
    Long contentLength,
    String contentType,
    ResourceZipStreamInfo.Writer writer
)
{
}
