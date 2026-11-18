package com.thinkfree.drive.global.security;

import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 애플리케이션 시작 시 {@link AnonymousAccessible}이 붙은 핸들러 메서드를 스캔하여
 * 익명 유저(ROLE_ANONYMOUS)가 접근 가능한 경로 매처를 구성한다.
 */
@Slf4j
@Component
public class AnonymousPathRegistry implements SmartInitializingSingleton {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private RequestMatcher matcher = null;

    public AnonymousPathRegistry(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        PathPatternRequestMatcher.Builder pathMatcher = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> matchers = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getMethod().isAnnotationPresent(AnonymousAccessible.class))
                .flatMap(entry -> {
                    var patterns = entry.getKey().getPatternValues();
                    var methods = entry.getKey().getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        return patterns.stream().map(pathMatcher::matcher);
                    }
                    return patterns.stream()
                            .flatMap(p -> methods.stream()
                                    .map(m -> pathMatcher.matcher(m.asHttpMethod(), p)));
                })
                .collect(Collectors.toList());

        if (matchers.isEmpty()) {
            log.warn("[AnonymousPathRegistry] no anonymous-accessible paths found");
            return;
        }

        matcher = new OrRequestMatcher(matchers);

        log.info("[AnonymousPathRegistry] registered {} anonymous-accessible path matcher(s):", matchers.size());
        matchers.forEach(m -> log.info("  - {}", m));
    }

    public boolean isAllowed(jakarta.servlet.http.HttpServletRequest request) {
        return matcher != null && matcher.matches(request);
    }
}
