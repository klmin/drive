package com.thinkfree.drive.weboffice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum WebOfficeMode {
    
    EDITOR("편집기"),
    VIEWER("뷰어모드");
    
    private final String name;
}
