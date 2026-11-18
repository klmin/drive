package com.thinkfree.drive.application.resource.info;

/** 다운로드 토큰 — 진입점별 URL 조립(경로·파라미터)은 어댑터 책임. */
public record ResourceDownloadLinkInfo
(
    String downloadToken
)
{
}
