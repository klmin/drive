package com.thinkfree.drive.application.share.command;

/**
 * 공유 대상 후보 사용자 검색 커맨드 — 웹 ShareUserSearchRequest(sk/ss)의 application 등가물.
 */
public record ShareUserSearchCommand
(
    String searchKeyword,
    Integer sharedByUserSeq
)
{
}
