package com.thinkfree.drive.global.jooq;

import com.thinkfree.drive.user.enums.UserLanguage;
import org.jooq.impl.AbstractConverter;

/**
 * jOOQ 생성 코드의 tb_user.user_language ↔ {@link UserLanguage} 변환 — {@code pom.xml} jooq-codegen 프로필의 forcedType 이 참조한다.
 * 규칙은 JPA {@link com.thinkfree.drive.global.jpa.UserLanguageConverter} 와 같다(name() 이 아니라 language 값, NULL 은 그대로 null).
 */
public final class UserLanguageJooqConverter extends AbstractConverter<String, UserLanguage> {

    public UserLanguageJooqConverter() {
        super(String.class, UserLanguage.class);
    }

    @Override
    public UserLanguage from(String databaseObject) {
        return databaseObject == null ? null : UserLanguage.from(databaseObject);
    }

    @Override
    public String to(UserLanguage userObject) {
        return userObject == null ? null : userObject.getLanguage();
    }
}
