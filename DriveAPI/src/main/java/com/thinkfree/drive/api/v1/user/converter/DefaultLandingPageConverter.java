package com.thinkfree.drive.api.v1.user.converter;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class DefaultLandingPageConverter implements Converter<String, DefaultLandingPage> {

    @Override
    public DefaultLandingPage convert(String source) {
        return DefaultLandingPage.from(source); // from 메서드에서 소문자 매핑 지원
    }
}
