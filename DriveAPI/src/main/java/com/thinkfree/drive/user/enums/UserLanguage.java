package com.thinkfree.drive.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserLanguage {
    KO("ko", "한국어"),
    EN("en", "영어"),
    ES("es", "스페인어"),
    AUTO("auto", "자동");

    private final String language;
    private final String title;

    @JsonCreator
    public static UserLanguage from(String language) {
        for (UserLanguage userLanguage : UserLanguage.values()) {
            if (userLanguage.getLanguage().equalsIgnoreCase(language)) {
                return userLanguage;
            }
        }
        throw new IllegalArgumentException("Unknown language: " + language);
    }

    @JsonValue
    public String getLanguage() {
        return language;
    }
}
