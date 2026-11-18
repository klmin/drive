package com.thinkfree.drive.global.jooq;

import org.jooq.impl.AbstractConverter;

/**
 * jOOQ 생성 코드의 char(1) 'Y'/'N' 플래그 ↔ Boolean 변환 — {@code pom.xml} jooq-codegen 프로필의 forcedType 이 참조한다.
 * 규칙은 구 JdbcClient 경로의 Y/N 변환(MyBatis BooleanTypeHandler)과 같다(쓰기 true→'Y'·false→'N', 읽기 'Y'(대소문자 무시)만 true, NULL 은 그대로 null).
 */
public final class YnBooleanConverter extends AbstractConverter<String, Boolean> {

    public YnBooleanConverter() {
        super(String.class, Boolean.class);
    }

    @Override
    public Boolean from(String databaseObject) {
        return databaseObject == null ? null : "Y".equalsIgnoreCase(databaseObject.trim());
    }

    @Override
    public String to(Boolean userObject) {
        return userObject == null ? null : (userObject ? "Y" : "N");
    }
}
