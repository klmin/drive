package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.user.enums.UserStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class UseStatusConverter implements Converter<String, UserStatus> {
    
    @Override
    public UserStatus convert(String source) {
        return UserStatus.from(source);
    }
}
