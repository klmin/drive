package com.thinkfree.drive.userlog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActorType {
    USER("USER", "사용자"),
    ADMIN("ADMIN", "관리자"),
    SYSTEM("SYSTEM", "시스템");

    private final String activityType;
    private final String value;
}
