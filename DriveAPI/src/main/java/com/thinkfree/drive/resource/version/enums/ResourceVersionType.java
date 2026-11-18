package com.thinkfree.drive.resource.version.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ResourceVersionType {
    CURRENT, HISTORY;
}
