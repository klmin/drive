package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.user.enums.MfaMethod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MfaMethodConverter implements Converter<String, MfaMethod> {
    @Override
    public MfaMethod convert(String source) {
        return MfaMethod.from(source);
    }
}
