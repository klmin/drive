package com.thinkfree.drive.application.apikey.command;

import com.thinkfree.drive.user.enums.RoleType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * 발급 요청 중 클라이언트가 보낸 값만 담는다.
 * 로그인 사용자 정보는 {@link com.thinkfree.drive.application.apikey.ApiKeyActor} 로 따로 받는다.
 */
@Builder
public record ApiKeyIssueCommand
(
    String keyName,
    String keyPurpose,
    Instant expireDate,
    /** api:read / api:write. 대상이 사용자 계정일 때만 지정한다 */
    List<String> scopes,
    /** 키가 속할 테넌트. 슈퍼관리자만 지정할 수 있다 */
    Integer tenantSeq,
    /** 대상 계정. 미지정이면 로그인 사용자 본인 */
    Integer targetUserSeq,
    /** 키 역할 — 슈퍼관리자가 다른 계정에 발급할 때만(대상 보유 역할 중 USER·ADMIN, 대상이 둘 다 보유하면 필수). 그 외 경로는 null */
    RoleType roleType,
    /** 보호 키 — 슈퍼관리자 경로만 true 로 보낸다(그 외 경로는 null = 보호 아님) */
    Boolean isProtected
)
{
}
