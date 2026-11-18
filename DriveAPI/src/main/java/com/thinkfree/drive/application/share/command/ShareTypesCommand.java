package com.thinkfree.drive.application.share.command;

/**
 * 사용 가능한 공유 유형 조회 커맨드 — 웹 ShareTypesRequest(resourceSeq/ss)의 application 등가물.
 */
public record ShareTypesCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq
)
{
}
