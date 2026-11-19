package com.thinkfree.drive.api.v1.upload.response;

/**
 * 취소 결과 — 이미 끝난 세션도 성공으로 응답하고 그 상태를 알려 준다.
 *
 * @param status 취소 후 세션 상태(취소됐으면 FAILED, 이미 완료됐으면 COMPLETED 등)
 */
public record UploadSessionCancelResponse
(
    String status
)
{
}
