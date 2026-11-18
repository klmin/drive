package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** 파일·폴더 이름 규칙 — 화면 이름 입력 검증과 같은 기준, 위반 시 RESOURCE_018 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResourceNameRule {

    static final int MAX_LENGTH = 255;

    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private static final Set<String> RESERVED_NAMES = Stream.of(
            Stream.of("CON", "PRN", "AUX", "NUL"),
            IntStream.rangeClosed(1, 9).mapToObj(i -> "COM" + i),
            IntStream.rangeClosed(1, 9).mapToObj(i -> "LPT" + i)
    ).flatMap(s -> s).collect(Collectors.toUnmodifiableSet());

    /** 폴더 이름·파일 전체 이름 검사 — 빈 이름, 앞뒤 공백, 마침표로 시작·끝, 255자 초과, 금지 문자, 예약어 거절 */
    public static void validate(String name) {
        if (name == null || name.isBlank()
                || !name.equals(name.strip())
                || name.startsWith(".") || name.endsWith(".")
                || name.length() > MAX_LENGTH
                || INVALID_CHARS.matcher(name).find()
                || isReserved(name)) {
            throw ApiRuntimeException.type(ResourceErrorCode.INVALID_NAME);
        }
    }

    /** 확장자를 뺀 파일 이름 검사 — 빈 이름, 끝 공백, 예약어 거절 */
    public static void validateBaseName(String baseName) {
        if (baseName.isEmpty() || !baseName.equals(baseName.stripTrailing()) || isReserved(baseName)) {
            throw ApiRuntimeException.type(ResourceErrorCode.INVALID_NAME);
        }
    }

    private static boolean isReserved(String name) {
        return RESERVED_NAMES.contains(name.toUpperCase(Locale.ROOT));
    }
}
