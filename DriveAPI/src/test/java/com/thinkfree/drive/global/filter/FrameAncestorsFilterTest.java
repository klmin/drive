package com.thinkfree.drive.global.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameAncestorsFilter — iframe 허용 범위")
class FrameAncestorsFilterTest {

    private MockHttpServletResponse filter(List<String> allowedOrigins) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new FrameAncestorsFilter(allowedOrigins).doFilter(new MockHttpServletRequest("GET", "/t/acme/login/api-key"), response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("허용 origin 이 있으면 같은 origin 과 그 목록만 허용")
    void withOrigins() throws ServletException, IOException {
        MockHttpServletResponse response = filter(List.of("https://developer.thinkfree.com", "https://dev.thinkfree.com"));

        assertThat(response.getHeader(FrameAncestorsFilter.HEADER))
                .isEqualTo("frame-ancestors 'self' https://developer.thinkfree.com https://dev.thinkfree.com");
    }

    @Test
    @DisplayName("허용 목록이 비어 있으면 같은 origin 만 허용")
    void empty() throws ServletException, IOException {
        assertThat(filter(List.of()).getHeader(FrameAncestorsFilter.HEADER)).isEqualTo("frame-ancestors 'self'");
    }
}
