package com.thinkfree.drive.branding.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum BrandingImageSource {
    CUSTOM, DEFAULT;

    @JsonCreator
    public static BrandingImageSource from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
