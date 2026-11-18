package com.thinkfree.drive.auth.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum JwtType {
    ACCESS("access"),
    REFRESH("refresh"),
    MFA_CHALLENGE("mfa_challenge"),
    MFA_SETUP_CHALLENGE("mfa_setup_challenge"),
    MFA_CONFIRM_CHALLENGE("mfa_confirm_challenge"),
    SIGN_UP("sign_up"),
    SSO_STATE("sso_state"),
    SSO_LOGIN("sso_login"),
    SSO_MFA_VERIFY("sso_mfa_verify"),
    SSO_MFA_SETUP("sso_mfa_setup"),
    SSO_ERROR("sso_error")
    ;

    private final String value;
    
    private static final Map<String, JwtType> JWT_TYPE_MAP;
    
    static {
        JWT_TYPE_MAP = new HashMap<>();
        for (JwtType type : JwtType.values()) {
            JWT_TYPE_MAP.put(type.getValue(), type);
        }
    }
    
    public static JwtType findByName(String name) {
        JwtType jwtType = JWT_TYPE_MAP.get(name);
        if (jwtType == null){
            throw new IllegalArgumentException("Unknown JwtType: " + name);
        }
        return jwtType;
    }
   
}
