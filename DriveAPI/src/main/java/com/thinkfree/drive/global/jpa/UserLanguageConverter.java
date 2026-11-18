package com.thinkfree.drive.global.jpa;

import com.thinkfree.drive.user.enums.UserLanguage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link UserLanguage} ↔ user_language 문자열("ko"/"en"/"es"/"auto") — MyBatis {@code UserLanguageTypeHandler} 와 동일(name() 이 아니라 language 값). */
@Converter
public class UserLanguageConverter implements AttributeConverter<UserLanguage, String> {

    @Override
    public String convertToDatabaseColumn(UserLanguage attribute) {
        return attribute == null ? null : attribute.getLanguage();
    }

    @Override
    public UserLanguage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserLanguage.from(dbData);
    }
}
