package com.thinkfree.drive.sso.identity.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserSsoIdentitySaveDto {
    private Integer userSeq;
    private Integer tenantSeq;
    private Integer ssoProviderSeq;
    private String subject;
    private String email;
    private String name;
}
