package com.thinkfree.drive.global.jpa;

import com.thinkfree.drive.user.entity.Password;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link Password} 값 객체 ↔ enc_pwd 문자열 (JPA 경로). MyBatis {@code PasswordTypeHandler} 와 동일 — 저장 값은 인코딩된 문자열 그대로. */
@Converter
public class PasswordConverter implements AttributeConverter<Password, String> {

    @Override
    public String convertToDatabaseColumn(Password attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Password convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Password.of(dbData);
    }
}
