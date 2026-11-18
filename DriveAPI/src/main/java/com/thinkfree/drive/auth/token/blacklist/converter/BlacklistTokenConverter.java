package com.thinkfree.drive.auth.token.blacklist.converter;

import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import com.thinkfree.drive.auth.model.LoginTokenClaims;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BlacklistTokenConverter {
    
    @Mapping(target = "tokenHash", source = "tokenHash")
    @Mapping(target = "userSeq", source = "claims.userSeq")
    @Mapping(target = "issuedDate", source = "claims.iat")
    @Mapping(target = "expirationDate", source = "claims.exp")
    BlacklistToken toSaveBlacklistToken(LoginTokenClaims claims, String tokenHash);
    
}
