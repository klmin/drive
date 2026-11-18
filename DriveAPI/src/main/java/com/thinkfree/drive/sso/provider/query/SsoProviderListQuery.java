package com.thinkfree.drive.sso.provider.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SsoProviderListQuery {
    private CursorPaginationQuery page;
    private Integer tenantSeq;
    private SsoProviderProtocolType protocolType;
}
