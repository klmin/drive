package com.thinkfree.drive.global.jpa;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link DefaultLandingPage} ↔ default_landing_page 문자열("my-drive"/"home") — MyBatis {@code DefaultLandingPageTypeHandler} 와 동일(name() 이 아니라 landingPage 값). */
@Converter
public class DefaultLandingPageConverter implements AttributeConverter<DefaultLandingPage, String> {

    @Override
    public String convertToDatabaseColumn(DefaultLandingPage attribute) {
        return attribute == null ? null : attribute.getLandingPage();
    }

    @Override
    public DefaultLandingPage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DefaultLandingPage.from(dbData);
    }
}
