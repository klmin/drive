package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class LoginTypeConverter implements Converter<String, LoginType> {
    @Override
    public LoginType convert(String source) {
        return LoginType.from(source);
    }
}
