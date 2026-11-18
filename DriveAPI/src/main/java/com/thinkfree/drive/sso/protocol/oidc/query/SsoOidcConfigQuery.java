package com.thinkfree.drive.sso.protocol.oidc.query;

import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import lombok.Builder;
import lombok.Getter;

public class SsoOidcConfigQuery {
    
    @Getter
    @Builder
    public static class Find implements SsoProtocolFindQuery {
        private Integer ssoProviderSeq;
    }
}
