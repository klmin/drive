package com.thinkfree.drive.application.admin.user.pendingdelete.command;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 삭제 대기 사용자 목록 조회 커맨드 — 웹 요청(AdminPendingDeleteUserListRequest + CursorPagingRequest)과 필드 동형.
 * 날짜는 일 단위 입력(LocalDate)이며 시각 경계(자정) 변환은 application CommandConverter가 담당한다.
 * 커서는 pendingDeleteDate(값) + userSeq(tie-breaker) 기준이라 cursorType은 실사용되지 않지만 웹 페이징 계약 그대로 유지한다.
 */
@Builder
public record AdminPendingDeleteUserListCommand
(
    LocalDate pendingDeleteStartDate,
    LocalDate pendingDeleteEndDate,
    LocalDate deleteScheduleStartDate,
    LocalDate deleteScheduleEndDate,
    Object cursorValue,
    Long cursorId,
    ResourceType cursorType,
    Integer pageSize
)
{
}
