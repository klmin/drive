package com.thinkfree.drive.application.share.command;

import java.util.List;

/**
 * 공유 대상 교체(전체 치환) 커맨드 — 웹 ShareTargetUpsertRequest(ss/ts)의 application 등가물.
 * targets 미제공(null)은 usecase가 빈 목록으로 정규화해 "전체 삭제"로 처리한다 (웹 계약 동일).
 */
public record ShareTargetReplaceCommand
(
    Integer sharedByUserSeq,
    List<ShareTargetItemCommand> targets
)
{
}
