package com.thinkfree.drive.global.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum AuthSource {
    INTERNAL,
    EXTERNAL
}
