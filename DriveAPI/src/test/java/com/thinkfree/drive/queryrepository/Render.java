package com.thinkfree.drive.queryrepository;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조회 결과를 기대값 문자열로 — 필드를 선언 순서로 {@code name=value} 로 늘어놓는다(null 필드는 생략).
 * 실행마다 바뀌는 값은 이름으로 바꾼다: 리소스·공유·사용자 seq 는 픽스처 이름({@code names}), 시각은 {@code t}(있음만), 그 밖의 seq 는 {@code #}(있음만).
 */
final class Render {

    private final Function<String, Function<Object, String>> byField;

    /** @param byField 필드 이름 → 값 변환(없으면 null — 기본 규칙) */
    Render(Function<String, Function<Object, String>> byField) {
        this.byField = byField;
    }

    String of(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Collection<?> c) {
            return c.stream().map(this::of).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof Map<?, ?> m) {
            return m.entrySet().stream().map(e -> of(e.getKey()) + ":" + of(e.getValue())).collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Instant) {
            return "t";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof Class<?>) {
            return String.valueOf(value);
        }
        StringBuilder sb = new StringBuilder("(");
        for (Field f : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Object v;
            try {
                v = f.get(value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            if (v == null) {
                continue;
            }
            Function<Object, String> custom = byField.apply(f.getName());
            String text = custom != null ? custom.apply(v) : f.getName().endsWith("Seq") && v instanceof Number ? "#" : of(v);
            if (sb.length() > 1) {
                sb.append(' ');
            }
            sb.append(f.getName()).append('=').append(text);
        }
        return sb.append(')').toString();
    }
}
