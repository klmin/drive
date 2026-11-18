package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.branding.enums.BrandingImageType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class BrandingImageTypeConverter implements Converter<String, BrandingImageType> {
    @Override
    public BrandingImageType convert(String source) {
        return BrandingImageType.fromPath(source);
    }
}
