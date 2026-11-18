package com.thinkfree.drive.document.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.messagesource.keys.MessageKeys;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;


@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum DocumentTemplateType {
    PPT(MessageKeys.DOCUMENT_NEW_PPT, "NewDoc.pptx"),
    WORD(MessageKeys.DOCUMENT_NEW_WORD,  "NewDoc.docx"),
    EXCEL(MessageKeys.DOCUMENT_NEW_EXCEL, "NewDoc.xlsx"),
    NOTE(MessageKeys.DOCUMENT_NEW_NOTE, "new.tfi")
    ;

    private static final String BASE_PATH = "documents/";
    
    @Getter
    private final String messageKey;
    private final String classpath;
    
    public String getClasspath() {
        return getClasspath(LocaleContextHolder.getLocale());
    }
    public String getClasspath(Locale locale) {
        return buildLocalizedPath(locale, classpath);
    }
    private String buildLocalizedPath(Locale locale, String relativePath) {
        if (locale == null) {
            return BASE_PATH + relativePath;
        }
        return BASE_PATH + locale.getLanguage() + DriveConstants.PATH_SEPARATOR + relativePath;
    }
    @JsonCreator
    public static DocumentTemplateType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
