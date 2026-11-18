package com.thinkfree.drive.global.jooq;

import org.jooq.Condition;
import org.jooq.Field;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

import static org.jooq.impl.DSL.falseCondition;

/** jOOQ 조건 조립 보조 — JdbcClient SQL(구 {@code global/jdbc/Sql}, 10-30 삭제)에서 옮길 때 판정 규칙을 그대로 맞춘다. */
public final class JooqConditions {

    private JooqConditions() {
    }

    /**
     * {@code column = :value} — 값이 null 이면 FALSE. 구 SQL 은 null 도 그대로 바인딩해 {@code = NULL}(참이 되지 않음)이었으므로 결과가 같다.
     * jOOQ {@code eq(null)} 은 {@code IS NULL} 로 바뀔 수 있어 쓰지 않는다.
     */
    public static <T> Condition eq(Field<T> field, T value) {
        return value == null ? falseCondition() : field.eq(value);
    }

    /** {@code column <> :value} — 값이 null 이면 FALSE(구 SQL 의 {@code <> NULL}). jOOQ {@code ne(null)} 은 {@code IS NOT NULL} 로 바뀔 수 있어 쓰지 않는다. */
    public static <T> Condition ne(Field<T> field, T value) {
        return value == null ? falseCondition() : field.ne(value);
    }

    /** 값이 "있음"인지 — null·빈 문자열·빈 컬렉션/맵/배열은 없음(구 {@code Sql.present}·MyBatis isNotEmpty 와 같은 판정). */
    public static boolean present(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }
}
