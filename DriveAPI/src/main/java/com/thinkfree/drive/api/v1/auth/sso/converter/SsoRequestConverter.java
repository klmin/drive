package com.thinkfree.drive.api.v1.auth.sso.converter;

import com.thinkfree.drive.api.v1.auth.sso.request.SsoSignupRequest;
import com.thinkfree.drive.auth.model.SsoSignupClaims;
import com.thinkfree.drive.sso.protocol.oidc.dto.OidcSignupDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SsoRequestConverter {
    
    @Mapping(target = "requestEmail", source = "request.e")
    @Mapping(target = "requestName", source = "request.n")
    OidcSignupDto toOidcSignupDto(SsoSignupClaims claims, SsoSignupRequest request);
}
