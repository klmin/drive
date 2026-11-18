package com.thinkfree.drive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestPathUtil — 세그먼트 추출·pathWithinApplication")
class RequestPathUtilTest {

    @ParameterizedTest
    @CsvSource({
            "/t/acme/api/v1, 0, t",
            "/t/acme/api/v1, 1, acme",
            "/t/acme/api/v1, 2, api",
            "/t/acme/api/v1, 3, v1",
            "/acme,          0, acme",
            "acme/x,         0, acme",
            "acme/x,         1, x"})
    @DisplayName("segment(path, index): 0 기준 인덱스, 선행 슬래시 유무 무관")
    void segment(String path, int index, String expected) {
        assertThat(RequestPathUtil.segment(path, index)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "/t/acme,  2",
            "/t/acme/, 2",
            "/t,       1",
            "/t/,      1",
            "/,        0",
            "/t/acme,  -1"})
    @DisplayName("segment: 범위 밖·음수 인덱스는 빈 문자열")
    void segmentOutOfRange(String path, int index) {
        assertThat(RequestPathUtil.segment(path, index)).isEmpty();
    }

    @Test
    @DisplayName("segment: 빈 세그먼트(/t//x 의 1)는 건너뛰지 않고 빈 문자열로 그대로 돌려준다")
    void segmentEmptyMiddle() {
        assertThat(RequestPathUtil.segment("/t//x", 1)).isEmpty();
        assertThat(RequestPathUtil.segment("/t//x", 2)).isEqualTo("x");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("segment/firstSegment: null·빈 문자열·공백은 빈 문자열")
    void blank(String path) {
        assertThat(RequestPathUtil.segment(path, 0)).isEmpty();
        assertThat(RequestPathUtil.firstSegment(path)).isEmpty();
    }

    @Test
    @DisplayName("firstSegment 는 segment(path, 0) 과 같다")
    void firstSegmentDelegates() {
        assertThat(RequestPathUtil.firstSegment("/acme/api/v1")).isEqualTo("acme");
        assertThat(RequestPathUtil.firstSegment("/")).isEmpty();
        assertThat(RequestPathUtil.firstSegment("acme")).isEqualTo("acme");
    }

    @Test
    @DisplayName("pathWithinApplication: servletPath+pathInfo 우선, 비어 있으면 requestURI − contextPath")
    void pathWithinApplication() {
        MockHttpServletRequest withServletPath = new MockHttpServletRequest();
        withServletPath.setRequestURI("/t/acme/api/v1/x");
        withServletPath.setServletPath("/api/v1/x");
        assertThat(RequestPathUtil.pathWithinApplication(withServletPath)).isEqualTo("/api/v1/x");

        MockHttpServletRequest uriOnly = new MockHttpServletRequest();
        uriOnly.setContextPath("/t/acme");
        uriOnly.setRequestURI("/t/acme/oauth2/token");
        assertThat(RequestPathUtil.pathWithinApplication(uriOnly)).isEqualTo("/oauth2/token");
    }
}
