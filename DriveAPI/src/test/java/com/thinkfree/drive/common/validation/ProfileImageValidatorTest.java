package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** 크기(5MB)·Content-Type·시그니처 조합별 판정 */
@DisplayName("ProfileImageValidator — 5MB 이하 PNG·JPG·GIF·WEBP")
class ProfileImageValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    private static MockMultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile("pif", "a.img", contentType, content);
    }

    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of("image/png", PNG),
                Arguments.of("image/jpeg", JPEG),
                Arguments.of("image/jpg", JPEG),
                Arguments.of("IMAGE/JPEG; charset=binary", JPEG),
                Arguments.of("image/gif", GIF),
                Arguments.of("image/webp", WEBP));
    }

    @ParameterizedTest
    @MethodSource("allowed")
    @DisplayName("허용 형식 — Content-Type 과 시그니처가 같은 형식이면 통과")
    void allowed(String contentType, byte[] content) {
        assertThatCode(() -> ProfileImageValidator.validateOrThrow(file(contentType, content))).doesNotThrowAnyException();
    }

    static Stream<Arguments> invalidFormat() {
        return Stream.of(
                Arguments.of("image/svg+xml", "<svg/>".getBytes(StandardCharsets.US_ASCII)),  // 허용 안 하는 형식
                Arguments.of("text/html", PNG),                                               // 내용은 PNG 지만 헤더가 이미지 아님
                Arguments.of("image/png", "<html></html>".getBytes(StandardCharsets.US_ASCII)), // 헤더만 이미지
                Arguments.of("image/png", JPEG),                                              // 헤더·내용 형식 불일치
                Arguments.of(null, PNG),                                                      // Content-Type 없음
                Arguments.of("image/png", new byte[0]));                                      // 빈 파일
    }

    @ParameterizedTest
    @MethodSource("invalidFormat")
    @DisplayName("형식 위반 — USER_033")
    void invalidFormat(String contentType, byte[] content) {
        assertError(file(contentType, content), UserErrorCode.PROFILE_IMAGE_INVALID_FORMAT);
    }

    @Test
    @DisplayName("크기 — 정확히 5MB 통과, 1바이트 초과는 USER_032")
    void size() {
        byte[] max = Arrays.copyOf(PNG, (int) ProfileImageValidator.MAX_SIZE_BYTES);
        byte[] over = Arrays.copyOf(PNG, (int) ProfileImageValidator.MAX_SIZE_BYTES + 1);

        assertThatCode(() -> ProfileImageValidator.validateOrThrow(file("image/png", max))).doesNotThrowAnyException();
        assertError(file("image/png", over), UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
    }

    private static void assertError(MockMultipartFile file, ErrorCode expected) {
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> ProfileImageValidator.validateOrThrow(file));
        assertThat(e.getCode()).isEqualTo(expected);
    }
}
