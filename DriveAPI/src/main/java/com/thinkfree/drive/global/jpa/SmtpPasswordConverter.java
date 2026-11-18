package com.thinkfree.drive.global.jpa;

import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link SmtpPassword} 값 객체 ↔ tb_smtp_config.enc_pwd 문자열(JPA 경로). MyBatis {@code SmtpPasswordTypeHandler} 와 동일 —
 * 저장 값은 AES 암호문 문자열 그대로. DB NULL 은 null — Hibernate 는 NULL 컬럼에 컨버터를 호출하지 않으므로 구 핸들러처럼 값 객체로 감쌀 수 없다(서비스는 항상 ""라도 암호화해 저장하므로 NULL 행은 없음).
 */
@Converter
public class SmtpPasswordConverter implements AttributeConverter<SmtpPassword, String> {

    @Override
    public String convertToDatabaseColumn(SmtpPassword attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public SmtpPassword convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SmtpPassword.of(dbData);
    }
}
