package com.thinkfree.drive.api.v1.upload.response;

/**
 * 세션 생성 응답 — 파트 계획은 서버가 정한다. uploadId 는 내보내지 않는다.
 *
 * @param sessionId          업로드 세션 ID(이후 모든 요청의 경로)
 * @param partSize           마지막을 뺀 파트 크기(byte)
 * @param partCount          파트 수 — 파트 번호는 1..partCount
 * @param maxConcurrentParts 세션당 동시에 보낼 파트 수(권장 상한)
 */
public record UploadSessionCreateResponse
(
    String sessionId,
    long partSize,
    int partCount,
    int maxConcurrentParts
)
{
}
