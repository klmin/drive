package com.thinkfree.drive.mcp.tool.response;

import java.util.List;
import java.util.Map;

/**
 * 내부 감사 로그 목록 API(GET /api/v1/admins/audit-logs, 거버넌스 프록시) 명세와 동형 계약 — 항목 필드는 웹 응답(AuditLogItemResponse) 전체와 동일하고,
 * 페이지 필드도 웹 page 봉투(ApiPage: totalCount/pageIndex/pageSize) 명명 그대로 평면 배치한다.
 * 봉투({data, error})만 MCP 규약. 날짜는 String — ToolDateTimeSupport 참고.
 */
public record AdminAuditLogListToolResponse
(
    List<AuditLogItem> logs,
    long totalCount,
    int pageIndex,
    int pageSize
)
{
    public record AuditLogItem(
            String eventId,
            String occurredAt,
            String eventType,
            String eventCategory,
            String actorType,
            String actorName,
            String actorEmail,
            String objectType,
            String objectName,
            String objectEmail,
            String objectPath,
            String outcomeStatus,
            String outcomeCode,
            String channel,
            String clientIp,
            String tenantSeq,
            String tenantName,
            Map<String, Object> change,
            List<Object> targets
    )
    {
    }
}
