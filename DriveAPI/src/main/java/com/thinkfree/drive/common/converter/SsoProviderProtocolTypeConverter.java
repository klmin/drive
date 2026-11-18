package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SsoProviderProtocolTypeConverter implements Converter<String, SsoProviderProtocolType> {
    @Override
    public SsoProviderProtocolType convert(String source) {
        return SsoProviderProtocolType.from(source);
    }
}
