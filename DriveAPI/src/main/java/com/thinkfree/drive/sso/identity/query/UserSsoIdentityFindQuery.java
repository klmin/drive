package com.thinkfree.drive.sso.identity.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserSsoIdentityFindQuery {
    private Integer ssoProviderSeq;
    private Integer tenantSeq;
    private String subject;
}
