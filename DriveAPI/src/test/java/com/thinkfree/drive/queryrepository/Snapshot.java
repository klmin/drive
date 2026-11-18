package com.thinkfree.drive.queryrepository;

import org.assertj.core.api.SoftAssertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 라벨 → 결과 문자열({@link Render}) 모음 — 예외는 예외 클래스 이름으로 남긴다. */
final class Snapshot {

    private final Render render;
    private final Map<String, String> got = new LinkedHashMap<>();

    Snapshot(Render render) {
        this.render = render;
    }

    void put(String label, Supplier<?> call) {
        Object value;
        try {
            value = call.get();
        } catch (RuntimeException e) {
            value = e.getClass().getSimpleName();
        }
        if (got.put(label, render.of(value)) != null) {
            throw new IllegalArgumentException("라벨 중복: " + label);
        }
    }

    /**
     * 라벨별로 비교 — 다른 라벨을 모두 모아 실패시킨다.
     * 픽스처를 바꿔 기대값을 다시 잡을 때는 현재 결과를 {@code target/snapshots/<name>.txt} 에 {@code Map.entry(...)} 줄로 남기므로 그걸 옮겨 붙인다.
     */
    void assertEquals(String name, Map<String, String> expected) {
        try {
            Path file = Path.of("target", "snapshots", name + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, got.entrySet().stream()
                    .map(e -> "                Map.entry(" + quote(e.getKey()) + ", " + quote(e.getValue()) + ")")
                    .collect(Collectors.joining(",\n", "", "\n")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(got.keySet()).as("라벨").containsExactlyInAnyOrderElementsOf(expected.keySet());
        got.forEach((label, value) -> soft.assertThat(value).as(label).isEqualTo(expected.get(label)));
        soft.assertAll();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    Map<String, String> values() {
        return got;
    }
}
