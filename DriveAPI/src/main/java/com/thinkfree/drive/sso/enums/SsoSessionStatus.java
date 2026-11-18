package com.thinkfree.drive.sso.enums;


import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SsoSessionStatus {
    LOGIN("login"),
    MFA_VERIFY("mfa_verify"),
    MFA_SETUP("mfa_setup"),
    SIGN_UP("sign_up"),
    ERROR("error");

    @JsonValue
    private final String value;
}
