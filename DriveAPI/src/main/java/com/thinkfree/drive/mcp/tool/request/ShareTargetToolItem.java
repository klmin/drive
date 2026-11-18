package com.thinkfree.drive.mcp.tool.request;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * drive_share_resource 툴의 공유 대상 입력 항목 — 웹 ShareTargetRequestItem(us/cv/ce/cd/cu/cs/se)의 MCP 등가물.
 * 웹은 권한 5종이 @NotNull 필수지만 LLM 입력은 생략이 잦아 툴이 기본값을 채운다
 * (canView는 모든 권한의 선행 조건이라 기본 true, 나머지 권한은 기본 false, sendEmail은 기본 true).
 * 스키마 required는 userSeq만 — Spring AI 스키마 생성기는 중첩 record 컴포넌트를 기본 required로 찍으므로
 * 선택 필드는 @ToolParam(required = false)로 표기한다(누락 시 MCP 클라이언트가 호출 전 검증에서 거부, 09-16 QA 리포트).
 */
public record ShareTargetToolItem
(
    @JsonPropertyDescription("공유 대상 사용자의 userSeq (필수)")
    Integer userSeq,

    @ToolParam(required = false)
    @JsonPropertyDescription("보기 권한 — 모든 권한의 선행 조건. 미지정 시 true")
    Boolean canView,

    @ToolParam(required = false)
    @JsonPropertyDescription("편집 권한. 미지정 시 false")
    Boolean canEdit,

    @ToolParam(required = false)
    @JsonPropertyDescription("다운로드 권한. 미지정 시 false")
    Boolean canDownload,

    @ToolParam(required = false)
    @JsonPropertyDescription("업로드 권한 (폴더 공유에서만 의미 있음). 미지정 시 false")
    Boolean canUpload,

    @ToolParam(required = false)
    @JsonPropertyDescription("공유(재공유·대상 관리) 권한 (SPECIFIC_USERS에서만 허용). 미지정 시 false")
    Boolean canShare,

    @ToolParam(required = false)
    @JsonPropertyDescription("이 대상이 새로 추가될 때 이메일 알림 발송 여부. 미지정 시 true")
    Boolean sendEmail
)
{
}
