package com.thinkfree.drive.sso.provider.query;

import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SsoProviderDisplayNameCheckQuery {
    private SsoProviderScope scope;
    private Integer tenantSeq;
    private String displayName;
    private Integer excludeSsoProviderSeq;
}
