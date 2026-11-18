package com.thinkfree.drive.tenant.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.tenant.enums.TenantType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TenantListQuery {
    private CursorPaginationQuery page;
    /** 유형 필터 — null 이면 전체 */
    private TenantType tenantType;
}
