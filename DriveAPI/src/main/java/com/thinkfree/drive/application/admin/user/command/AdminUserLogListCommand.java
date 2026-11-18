package com.thinkfree.drive.application.admin.user.command;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;

/**
 * 사용자 Log 목록 조회 커맨드 — 웹 커서 페이징 요청(CursorPagingRequest)과 필드 동형.
 * 커서는 userLogSeq 단일 기준이라 cursorValue/cursorType은 실사용되지 않지만 웹 페이징 계약 그대로 유지한다.
 */
@Builder
public record AdminUserLogListCommand
(
    Object cursorValue,
    Long cursorId,
    ResourceType cursorType,
    Integer pageSize
)
{
}
