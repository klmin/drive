package com.thinkfree.drive.locale.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SupportedLocale {
    KOREAN("ko"),
    ENGLISH("en")
    ;

    private final String language;

    private static final Map<String, SupportedLocale> LANGUAGE_MAP;

    static {
        LANGUAGE_MAP = new HashMap<>();
        for (SupportedLocale enums : SupportedLocale.values()) {
            LANGUAGE_MAP.put(enums.language, enums);
        }
    }

    public static SupportedLocale findByLanguage(String language) {
        if(!StringUtils.hasText(language)){
            return KOREAN;
        }
        SupportedLocale locale = LANGUAGE_MAP.get(language.toLowerCase());
        return locale != null ? locale : KOREAN;
    }


}
