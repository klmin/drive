package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.RoleType;

import java.util.List;

/**
 * 내부 관리자 API Key 목록 API 명세와 동형 계약 — 항목 필드는 웹 응답(ApiKeyListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * Info의 tenantSeq·tenantName은 웹 관리자 응답과 동일하게 제외한다(자기 테넌트 고정이라 무의미).
 * 키는 maskedApiKey(전체 마스킹)만 — 원문은 발급·재발급 응답에서만 볼 수 있다.
 * 날짜는 String — ToolDateTimeSupport 참고. 커서가 apiKeySeq 단일 기준이라
 * nextCursorValue/nextCursorType은 항상 null이다 (웹 계약 동일 — 페이지 이동은 nextCursor만 쓴다).
 */
public record AdminApiKeyListToolResponse
(
    List<AdminApiKeyItem> apiKeys,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record AdminApiKeyItem(
            Integer apiKeySeq,
            String keyName,
            String keyPurpose,
            String maskedApiKey,
            /* 키가 행세하는 대상 계정 — 관리자가 사용자 대신 발급한 키가 섞여 있음 */
            Integer userSeq,
            String userId,
            String userName,
            RoleType roleType,
            /* 키에 부여된 scope. 관리자 키도 값을 갖지만 검사 대상은 아니다 */
            List<String> scopes,
            ApiKeyStatus status,
            String expireDate,
            /* 만료 키도 목록에 나온다 — 만료일을 연장하면 다시 쓸 수 있음 */
            Boolean isExpired,
            String lastUsedDate,
            String registerDate,
            /* 보호 키 — 슈퍼관리자가 보호 키로 지정한 키(발급 pt=true 또는 보호 설정 API)로, 관리자 경로에서는 만료일·활성화·비활성화·재발급이 거부된다(API_KEY_PROTECTED) */
            Boolean isProtected
    )
    {
    }
}
