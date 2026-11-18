package com.thinkfree.drive.tenant.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 픽스처 = {@code src/test/resources/static/}( {@code _next/static/x.js}, {@code common/a.svg}, {@code favicon.ico}, {@code drive.html} ).
 * 테스트 classpath 에는 메인 번들({@code target/classes/static}, 프론트 포함 빌드 시)도 같이 잡히므로 false 검증은 번들에도 없는 이름만 쓴다.
 */
@DisplayName("StaticBundleIndex — 정적 번들 최상위 세그먼트 스캔")
class StaticBundleIndexTest {

    private final StaticBundleIndex index = new StaticBundleIndex(new PathMatchingResourcePatternResolver());

    @ParameterizedTest
    @ValueSource(strings = {"_next", "common", "favicon.ico", "drive.html", "drive"})
    @DisplayName("번들에 존재하는 디렉터리·파일·html 확장자 생략형은 true")
    void rootSegmentsFromBundle(String segment) {
        assertThat(index.isRootSegment(segment)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "acme", "static", "x.js", "a.svg", "_next/static", "/_next", "NEXT", "_Next"})
    @DisplayName("번들에 없는 이름·중첩 경로·다른 대소문자는 false (임의 경로 우회 없음)")
    void nonBundleNames(String segment) {
        assertThat(index.isRootSegment(segment)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("null·빈 문자열·공백은 false")
    void blank(String segment) {
        assertThat(index.isRootSegment(segment)).isFalse();
    }

    @Test
    @DisplayName("스캔이 실패하면 빈 집합 — 필터는 아무것도 스킵하지 않는다")
    void scanFailureYieldsEmptyIndex() throws IOException {
        ResourcePatternResolver failing = mock(ResourcePatternResolver.class);
        when(failing.getResources(anyString())).thenThrow(new IOException("boom"));

        StaticBundleIndex empty = new StaticBundleIndex(failing);

        assertThat(empty.isRootSegment("_next")).isFalse();
    }
}
