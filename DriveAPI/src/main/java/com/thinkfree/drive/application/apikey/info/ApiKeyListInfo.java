package com.thinkfree.drive.application.apikey.info;

import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.user.enums.RoleType;

import java.time.Instant;
import java.util.List;

/**
 * 목록 항목. 도메인이 줄 수 있는 값을 모두 담고, 무엇을 내보낼지는 각 진입점이 고른다.
 * 사용자 경로는 본인 키만 보므로 대상 계정·테넌트 필드를 응답에 넣지 않는다.
 */
public record ApiKeyListInfo
(
    Integer apiKeySeq,
    String keyName,
    String keyPurpose,
    /** 고정 길이 '*'(SYSTEM_MANAGED + SINGLE 은 블러용 값) — 키 원문은 발급·재발급 응답에서만 볼 수 있다 */
    String maskedApiKey,
    Integer tenantSeq,
    String tenantName,
    Integer userSeq,
    String userId,
    String userName,
    RoleType roleType,
    /** 키에 부여된 scope. 관리자·슈퍼관리자 키도 값을 갖지만 검사 대상은 아니다 */
    List<String> scopes,
    ApiKeyStatus status,
    Instant expireDate,
    /** DB NOW() 기준 — 인증 판정과 같은 시계 */
    Boolean isExpired,
    Instant lastUsedDate,
    Instant registerDate,
    /** 보호 키 — 만료일·상태·재발급은 슈퍼관리자 경로에서만 */
    Boolean isProtected
)
{
}
