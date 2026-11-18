package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.branding.enums.BrandingImageSource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class BrandingImageSourceConverter implements Converter<String, BrandingImageSource> {
    @Override
    public BrandingImageSource convert(String source) {
        return BrandingImageSource.from(source);
    }
}
