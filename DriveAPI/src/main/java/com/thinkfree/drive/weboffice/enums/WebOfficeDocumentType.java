package com.thinkfree.drive.weboffice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum WebOfficeDocumentType {
    WRITE("한워드"),
    CALC("한셀"),
    SHOW("한쇼"),
    HWP("한글"),
    PDF("PDF");
    
    private final String name;
}
