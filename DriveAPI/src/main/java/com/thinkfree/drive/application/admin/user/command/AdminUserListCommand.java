package com.thinkfree.drive.application.admin.user.command;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;

import java.util.List;

/**
 * 관리자용 사용자 목록 조회 커맨드 — 웹 요청(AdminUserListRequest + CursorPagingRequest)과 필드 동형.
 * 직접 생성하는 어댑터(MCP 툴)는 builder로 조립한다 (웹 어댑터는 MapStruct RequestConverter가 이름 기반으로 생성).
 * 커서는 userSeq 단일 기준이라 cursorValue/cursorType은 이 목록에서 실사용되지 않지만 웹 페이징 계약 그대로 유지한다.
 */
@Builder
public record AdminUserListCommand
(
    UserStatus status,
    String userId,
    List<RoleType> roles,
    Object cursorValue,
    Long cursorId,
    ResourceType cursorType,
    Integer pageSize
)
{
}
