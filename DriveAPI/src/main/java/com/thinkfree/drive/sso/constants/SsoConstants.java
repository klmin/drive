package com.thinkfree.drive.sso.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SsoConstants {
    public static final String SSO_PATH = "/api/v1/auth/sso";
    public static final String SSO_SESSION_TOKEN = "sso_session_token";
    
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Oidc {
        public static final String OIDC_CALLBACK_SUFFIX = "/callback/oidc";
        public static final String OIDC_CALLBACK_PATH = SSO_PATH + OIDC_CALLBACK_SUFFIX;
        public static final String DISCOVERY_PATH        = "/.well-known/openid-configuration";
        public static final String DEFAULT_SCOPES        = "openid email profile";
        public static final String DEFAULT_RESPONSE_TYPE = "code";
        public static final String PARAM_RESPONSE_TYPE   = "response_type";
        public static final String PARAM_CLIENT_ID       = "client_id";
        public static final String PARAM_REDIRECT_URI    = "redirect_uri";
        public static final String PARAM_SCOPE           = "scope";
        public static final String PARAM_STATE           = "state";
        public static final String PARAM_GRANT_TYPE      = "grant_type";
        public static final String PARAM_CODE            = "code";
        public static final String DEFAULT_GRANT_TYPE    = "authorization_code";
    }
}
