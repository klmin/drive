package com.thinkfree.drive.app.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.thinkfree.drive.app.constants.ProfileConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum AppProfile {
    LOCAL(ProfileConstants.LOCAL),
    DEV(ProfileConstants.DEV),
    STAGE(ProfileConstants.STAGE),
    PROD(ProfileConstants.PROD);
    
    @JsonValue
    private final String value;
}
