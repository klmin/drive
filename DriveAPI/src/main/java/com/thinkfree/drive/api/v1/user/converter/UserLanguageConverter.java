package com.thinkfree.drive.api.v1.user.converter;

import com.thinkfree.drive.user.enums.UserLanguage;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class UserLanguageConverter implements Converter<String, UserLanguage> {

    @Override
    public UserLanguage convert(String source) {
        return UserLanguage.from(source); // from 메서드에서 소문자 매핑 지원
    }
}
