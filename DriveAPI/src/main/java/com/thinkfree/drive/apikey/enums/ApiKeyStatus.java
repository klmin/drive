package com.thinkfree.drive.apikey.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ApiKeyStatus {
    ACTIVE,
    INACTIVE
    ;
}
