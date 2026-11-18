package com.thinkfree.drive.sso.protocol.jwt.query;

import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import lombok.Builder;
import lombok.Getter;

public class SsoJwtConfigQuery {

    @Getter
    @Builder
    public static class Find implements SsoProtocolFindQuery {
        private Integer ssoProviderSeq;
    }
}
