package com.thinkfree.drive.mcp.tool.request;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * drive_admin_bulk_upsert_users 툴의 항목 입력 — 웹 AdminUserBulkUpsertRequest.UserItem(us/ui/encPwd/un/tq/roles/ct/pn)의 MCP 등가물.
 * 축약 필드명만 전체 표기로 바꿨고(password → 웹 encPwd, 원문 비밀번호) 의미·필수 규칙은 웹과 동일하다(사용자 결정 09-10: 웹 완전 동형).
 * userSeq가 null이면 신규 등록(userId·password·roles 필수), 있으면 수정 — 수정은 선조회·병합 없이 userName·totalQuotaByte·countryCode·phoneNumber를
 * 전부 덮어쓰므로(하부 UPDATE SQL이 4컬럼 전체 교체) 생략한 국가코드·전화번호는 NULL로 지워진다. 단 roles 는 생략하면 역할 유지(웹과 동일).
 * 스키마 required는 항상 필수인 userName·totalQuotaByte만 — Spring AI 스키마 생성기는 중첩 record 컴포넌트를 기본 required로 찍으므로
 * 조건부 필수(등록 시만)·선택 필드는 @ToolParam(required = false)로 표기한다(등록 필수 누락은 툴 본문 선검증이 users[i].필드명으로 인밴드 차단).
 */
public record UserUpsertToolItem
(
    @ToolParam(required = false)
    @JsonPropertyDescription("수정할 기존 사용자의 userSeq. 생략(null)하면 신규 등록, 지정하면 그 계정을 수정")
    Integer userSeq,

    @ToolParam(required = false)
    @JsonPropertyDescription("로그인 ID — 이메일 형식. 신규 등록 시 필수, 수정 시 무시됨")
    String userId,

    @ToolParam(required = false)
    @JsonPropertyDescription("초기 비밀번호 원문 — 8~64자, 대문자·소문자·숫자·특수문자 각 1자 이상. 신규 등록 시 필수, 수정 시 무시됨. 임시 비밀번호 권장(대화 기록에 평문으로 남음)")
    String password,

    @JsonPropertyDescription("사용자 이름 — 최대 20자 (필수, 등록·수정 모두)")
    String userName,

    @ToolParam(required = false)
    @JsonPropertyDescription("할당 용량(byte) — 역할에 USER 가 있으면 필수(0보다 커야 함), ADMIN 만이면 생략 또는 0(수정 항목은 수정 후 역할 기준). 수정 시 현재 사용량보다 작게 줄일 수 없음")
    Long totalQuotaByte,

    @ToolParam(required = false)
    @JsonPropertyDescription("역할 목록 — USER·ADMIN 중 1개 이상. 신규 등록 시 필수. 수정 시 지정하면 보유 역할을 이 목록으로 교체(USER 회수는 사용 중인 용량이 없을 때만 — USER_030, 본인 ADMIN 회수 불가 — USER_031), 생략하면 역할 유지")
    List<RoleType> roles,

    @ToolParam(required = false)
    @JsonPropertyDescription("전화번호 국가코드 — '+'를 뺀 숫자, 최대 4자 (예: 82). 전화번호와 함께 지정. 수정 시 생략하면 NULL로 지워짐")
    String countryCode,

    @ToolParam(required = false)
    @JsonPropertyDescription("전화번호 — '-'를 뺀 숫자, 최대 20자 (예: 01012345678). 국가코드와 함께 지정. 수정 시 생략하면 NULL로 지워짐")
    String phoneNumber
)
{
}
