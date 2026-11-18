package com.thinkfree.drive.weboffice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum WebOfficeType {
    
    WRITE_EDITOR("한워드 편집기", WebOfficeDocumentType.WRITE, WebOfficeMode.EDITOR),
    WRITE_VIEWER("한워드 뷰어모드", WebOfficeDocumentType.WRITE, WebOfficeMode.VIEWER),
    CALC_EDITOR("한셀 편집기", WebOfficeDocumentType.CALC, WebOfficeMode.EDITOR),
    CALC_VIEWER("한셀 뷰어모드", WebOfficeDocumentType.CALC, WebOfficeMode.VIEWER),
    SHOW_EDITOR("한쇼 편집기", WebOfficeDocumentType.SHOW, WebOfficeMode.EDITOR),
    SHOW_VIEWER("한쇼 뷰어모드", WebOfficeDocumentType.SHOW, WebOfficeMode.VIEWER),
    HWP_EDITOR("한글 편집기", WebOfficeDocumentType.HWP, WebOfficeMode.EDITOR),
    HWP_VIEWER("한글 뷰어모드", WebOfficeDocumentType.HWP, WebOfficeMode.VIEWER),
    PDF_EDITOR("PDF 편집기", WebOfficeDocumentType.PDF, WebOfficeMode.EDITOR),
    PDF_VIEWER("PDF 뷰어모드", WebOfficeDocumentType.PDF, WebOfficeMode.VIEWER),
    ;
    
    private final String name;
    private final WebOfficeDocumentType documentType;
    private final WebOfficeMode mode;

    public boolean isEditer() { return this.mode == WebOfficeMode.EDITOR; }

}
