package com.thinkfree.drive.config.security;

import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Set;

public class CustomSecurityContextFactory implements WithSecurityContextFactory<WithMockUserCustom> {
    @Override
    public SecurityContext createSecurityContext(WithMockUserCustom user) {

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        var userDetailDto = new UserDetailDto(user.userSeq(), user.userId(), user.userName(), user.roles(),
                user.authSource(), user.tenantSeq(), Set.of(user.scopes()));
        UserDetails userDetails = new CustomUserDetails(userDetailDto);

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
        context.setAuthentication(token);

        return context;
    }
}
