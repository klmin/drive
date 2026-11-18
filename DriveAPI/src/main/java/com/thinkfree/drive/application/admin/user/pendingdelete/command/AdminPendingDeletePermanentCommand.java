package com.thinkfree.drive.application.admin.user.pendingdelete.command;

import lombok.Builder;

import java.util.List;

/**
 * 삭제 대기 사용자 완전 삭제 커맨드 — 웹 요청(AdminPendingDeletePermanentRequest.ul)과 동형.
 * 비어 있지 않음 검증(@NotEmpty)은 어댑터 request가 담당한다.
 */
@Builder
public record AdminPendingDeletePermanentCommand
(
    List<Integer> userSeqList
)
{
}
