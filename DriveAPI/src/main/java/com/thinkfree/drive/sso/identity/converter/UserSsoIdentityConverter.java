package com.thinkfree.drive.sso.identity.converter;

import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;
import com.thinkfree.drive.sso.identity.entity.UserSsoIdentity;
import com.thinkfree.drive.sso.identity.query.UserSsoIdentityFindQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserSsoIdentityConverter {
    UserSsoIdentityFindQuery toSsoIdentityFindQuery(Integer ssoProviderSeq, Integer tenantSeq, String subject);
    UserSsoIdentity toSaveUserSsoIdentity(UserSsoIdentitySaveDto dto);
    UserSsoIdentitySaveDto toIdentitySaveDto(Integer userSeq, Integer tenantSeq, Integer ssoProviderSeq, String subject, String email, String name);
}
