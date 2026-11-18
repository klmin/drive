package com.thinkfree.drive.application.apikey.command;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;

/**
 * 목록 조회 요청 중 클라이언트가 보낸 값만 담는다.
 * 조회 범위는 {@link com.thinkfree.drive.application.apikey.ApiKeyScope} 로 따로 받는다.
 */
@Builder
public record ApiKeyListCommand
(
    /** 슈퍼관리자만 쓰는 필터 — 지정하면 그 테넌트로 좁힌다. 범위를 정하는 값이 아니다 */
    Integer tenantSeq,
    /** 관리자·슈퍼관리자의 본인 발급 키 필터(mine) — 지정하면 그 사용자가 발급한 키로 좁힌다. 범위를 정하는 값이 아니다 */
    Integer registerUserSeq,
    Object cursorValue,
    Long cursorId,
    /** API Key 목록은 리소스가 아니라 항상 null — 커서 페이징 공통 규격이라 필드만 유지한다 */
    ResourceType cursorType,
    Integer pageSize
)
{
}
