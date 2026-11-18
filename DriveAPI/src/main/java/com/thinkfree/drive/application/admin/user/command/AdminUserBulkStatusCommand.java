package com.thinkfree.drive.application.admin.user.command;

import java.util.List;

/** 사용자 상태 일괄 조회 커맨드 — 웹 요청(AdminUserBulkStatusRequest)과 동형 */
public record AdminUserBulkStatusCommand
(
    List<String> userIds
)
{
}
