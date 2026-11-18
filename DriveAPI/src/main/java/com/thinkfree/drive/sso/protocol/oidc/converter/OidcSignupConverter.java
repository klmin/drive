package com.thinkfree.drive.sso.protocol.oidc.converter;

import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;
import com.thinkfree.drive.sso.protocol.oidc.dto.OidcSignupDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OidcSignupConverter {
    
    UserSsoIdentitySaveDto toUserSsoIdentitySaveDto(OidcSignupDto dto, int userSeq);
}
