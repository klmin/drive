package com.thinkfree.drive.userlog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityType {
    LOGIN("LOGIN", "로그인"),
    LOGOUT("LOGOUT", "로그아웃"),
    CHANGE_PASSWORD("CHANGE_PASSWORD", "비밀번호변경");

    private final String activityType;
    private final String value;
}
