package com.thinkfree.drive.user.enums;

import com.thinkfree.drive.constants.DriveConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum ProfileType {
    PROFILE("profiles/");

    private final String basePath;

    public String buildStoragePath(Locale locale, String relativePath) {
        if (locale == null) {
            return basePath + relativePath;
        }
        return basePath + locale.getLanguage() + DriveConstants.PATH_SEPARATOR + relativePath;
    }
}