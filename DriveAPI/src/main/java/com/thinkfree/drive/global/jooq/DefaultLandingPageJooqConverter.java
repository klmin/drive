package com.thinkfree.drive.global.jooq;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import org.jooq.impl.AbstractConverter;

/**
 * jOOQ 생성 코드의 tb_user.default_landing_page ↔ {@link DefaultLandingPage} 변환 — {@code pom.xml} jooq-codegen 프로필의 forcedType 이 참조한다.
 * 규칙은 JPA {@link com.thinkfree.drive.global.jpa.DefaultLandingPageConverter} 와 같다(name() 이 아니라 landingPage 값, NULL 은 그대로 null).
 */
public final class DefaultLandingPageJooqConverter extends AbstractConverter<String, DefaultLandingPage> {

    public DefaultLandingPageJooqConverter() {
        super(String.class, DefaultLandingPage.class);
    }

    @Override
    public DefaultLandingPage from(String databaseObject) {
        return databaseObject == null ? null : DefaultLandingPage.from(databaseObject);
    }

    @Override
    public String to(DefaultLandingPage userObject) {
        return userObject == null ? null : userObject.getLandingPage();
    }
}
