package com.thinkfree.drive.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DefaultLandingPage {
    MY_DRIVE("my-drive", "내드라이브"),
    HOME("home", "홈");

    private final String landingPage;
    private final String title;

    @JsonCreator
    public static DefaultLandingPage from(String landingPage) {
        for (DefaultLandingPage page : values()) {
            if (page.getLandingPage().equalsIgnoreCase(landingPage)) {
                return page;
            }
        }
        throw new IllegalArgumentException("Unknown landing page: " + landingPage);
    }

    @JsonValue
    public String getLandingPage() {
        return landingPage;
    }
}
