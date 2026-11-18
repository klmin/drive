package com.thinkfree.drive.global.security;

import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnonymousPathRegistry 단위 테스트")
class AnonymousPathRegistryTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    private AnonymousPathRegistry registry;

    @RequestMapping("/api/v1")
    static class TestController {

        @GetMapping("/resources/{resourceSeq}")
        @AnonymousAccessible
        public void viewResource() {}

        @GetMapping("/files/{resourceSeq}/download")
        @AnonymousAccessible
        public void downloadFile() {}

        @PostMapping("/resources/download-token")
        @AnonymousAccessible
        public void generateDownloadToken() {}

        @PostMapping("/token/reissue")
        @AnonymousAccessible
        public void reissueTokens() {}

        // 어노테이션 없음 — 차단 대상
        @GetMapping("/resources/no-annotation")
        public void noAnnotation() {}

        @PostMapping("/files/{resourceSeq}/move")
        public void moveFile() {}
    }

    private void setupMapping(String path, String methodName) throws NoSuchMethodException {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
                TestController.class.getMethod(methodName));
        RequestMappingInfo mappingInfo = RequestMappingInfo.paths(path).build();
        given(handlerMapping.getHandlerMethods()).willReturn(Map.of(mappingInfo, handlerMethod));

        registry = new AnonymousPathRegistry(handlerMapping);
        registry.afterSingletonsInstantiated();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        req.setServletPath(path);
        return req;
    }

    @Nested
    @DisplayName("@AnonymousAccessible — 허용 케이스")
    class Allowed {

        @Test
        @DisplayName("GET 경로에 @AnonymousAccessible → 허용")
        void get_allowed() throws Exception {
            setupMapping("/api/v1/resources/{resourceSeq}", "viewResource");
            assertThat(registry.isAllowed(request("/api/v1/resources/123"))).isTrue();
        }

        @Test
        @DisplayName("GET 다운로드 경로에 @AnonymousAccessible → 허용")
        void download_allowed() throws Exception {
            setupMapping("/api/v1/files/{resourceSeq}/download", "downloadFile");
            assertThat(registry.isAllowed(request("/api/v1/files/123/download"))).isTrue();
        }

        @Test
        @DisplayName("POST 경로에 @AnonymousAccessible → 허용")
        void post_allowed() throws Exception {
            setupMapping("/api/v1/resources/download-token", "generateDownloadToken");
            assertThat(registry.isAllowed(request("/api/v1/resources/download-token"))).isTrue();
        }

        @Test
        @DisplayName("토큰 재발급 경로에 @AnonymousAccessible → 허용")
        void tokenReissue_allowed() throws Exception {
            setupMapping("/api/v1/token/reissue", "reissueTokens");
            assertThat(registry.isAllowed(request("/api/v1/token/reissue"))).isTrue();
        }
    }

    @Nested
    @DisplayName("@AnonymousAccessible 없음 — 차단 케이스")
    class Blocked {

        @Test
        @DisplayName("어노테이션 없는 GET 경로 → 차단")
        void noAnnotation_blocked() throws Exception {
            setupMapping("/api/v1/resources/no-annotation", "noAnnotation");
            assertThat(registry.isAllowed(request("/api/v1/resources/no-annotation"))).isFalse();
        }

        @Test
        @DisplayName("어노테이션 없는 POST 경로 → 차단")
        void noAnnotationPost_blocked() throws Exception {
            setupMapping("/api/v1/files/{resourceSeq}/move", "moveFile");
            assertThat(registry.isAllowed(request("/api/v1/files/123/move"))).isFalse();
        }
    }
}
