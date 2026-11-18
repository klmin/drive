package com.thinkfree.drive.apikey.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import lombok.Builder;
import lombok.Getter;

/**
 * 목록 조회 조건. 조회 주체의 role 에 따라 채워지는 필드가 다르다.
 * <ul>
 *     <li>사용자 — {@code registerUserSeq} (본인이 발급한 키)</li>
 *     <li>관리자 — {@code tenantSeq} (자기 테넌트의 ADMIN·USER role 키). {@code registerUserSeq} 를 주면 본인 발급 키로 좁힌다</li>
 *     <li>슈퍼관리자 — 없음. {@code tenantSeq}·{@code registerUserSeq} 를 주면 그 조건으로 좁힌다</li>
 * </ul>
 */
@Builder
@Getter
public class ApiKeyListQuery {
    private Integer tenantSeq;
    private Integer registerUserSeq;
    private CursorPaginationQuery page;
}
