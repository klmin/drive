package com.thinkfree.drive.api.v1.upload.response;

/**
 * complete 결과 — 같은 세션 재요청은 같은 값을 돌려준다.
 *
 * @param resourceSeq  저장된 파일
 * @param resourceName 확정 이름(같은 이름이 있으면 넘버링된 이름)
 */
public record UploadSessionCompleteResponse
(
    Long resourceSeq,
    String resourceName
)
{
}
