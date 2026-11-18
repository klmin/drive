package com.thinkfree.drive.config.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.global.enums.AuthSource;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = CustomSecurityContextFactory.class)
public @interface WithMockUserCustom {

    int userSeq() default 1;
    String userId() default "admin@thinkfree.com";
    String userName() default "관리자";
    String roles() default "ROLE_ADMIN";
    AuthSource authSource() default AuthSource.INTERNAL;
    int tenantSeq() default 1;

    /** 외부 API Key 인증에만 의미가 있다. 내부 JWT 는 scope 개념이 없어 비워 둔다 */
    ApiKeyScope[] scopes() default {};
}
