package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.constants.DriveConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 애플리케이션 시작 시 {@link RequiredApiScope} 가 붙은 핸들러 메서드를 스캔하여
 * 외부 경로별 요구 scope 를 구성한다.
 * <p>
 * 컨트롤러 소스가 아니라 실제 등록된 매핑을 훑으므로, 한 메서드에 path 가 여러 개면 전부,
 * {@code @RequestMapping} 에 method 가 없으면 전 메서드가 대상이 된다.
 */
@Slf4j
@Component
public class ApiScopeRegistry implements SmartInitializingSingleton {

    /** method 조건이 없는 매핑 — 전 HTTP 메서드가 대상이다 */
    private static final String ANY_METHOD = "ANY";

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private List<ScopeRule> rules = List.of();

    public ApiScopeRegistry(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        PathPatternRequestMatcher.Builder pathMatcher = PathPatternRequestMatcher.withDefaults();

        rules = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getMethod().isAnnotationPresent(RequiredApiScope.class))
                .flatMap(entry -> {
                    ApiKeyScope required = entry.getValue().getMethod().getAnnotation(RequiredApiScope.class).value();
                    List<String> patterns = entry.getKey().getPatternValues().stream()
                            .filter(pattern -> pattern.startsWith(DriveConstants.API_PUBLIC_V1_PREFIX))
                            .toList();
                    var methods = entry.getKey().getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        return patterns.stream()
                                .map(p -> new ScopeRule(ANY_METHOD + " " + p, pathMatcher.matcher(p), required));
                    }
                    return patterns.stream().flatMap(p -> methods.stream()
                            .map(m -> new ScopeRule(m.name() + " " + p, pathMatcher.matcher(m.asHttpMethod(), p), required)));
                })
                .toList();

        warnIfDeclaredButNotExternal();

        log.info("[ApiScopeRegistry] registered {} scope rule(s):", rules.size());
        rules.forEach(rule -> log.info("  - {} -> {}", rule.key(), rule.scope().getValue()));
    }

    /** 요청에 요구되는 scope. 선언이 없으면 null — 검사 대상이 아니다 */
    public ApiKeyScope requiredScope(HttpServletRequest request) {
        return rules.stream()
                .filter(rule -> rule.matcher().matches(request))
                .map(ScopeRule::scope)
                .findFirst()
                .orElse(null);
    }

    /** 등록된 규칙 — {@code "<HTTP method> <path>"} → 요구 scope. 목록 파일과 대조하는 테스트용 */
    public Map<String, ApiKeyScope> declaredRules() {
        return rules.stream().collect(Collectors.toMap(ScopeRule::key, ScopeRule::scope, (a, b) -> a));
    }

    /** {@code @RequiredApiScope} 만 붙고 {@code @ExternalApi} 가 빠지면 규칙이 조용히 사라진다 */
    private void warnIfDeclaredButNotExternal() {
        requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getMethod().isAnnotationPresent(RequiredApiScope.class))
                .filter(entry -> entry.getKey().getPatternValues().stream()
                        .noneMatch(pattern -> pattern.startsWith(DriveConstants.API_PUBLIC_V1_PREFIX)))
                .map(entry -> entry.getValue().getMethod())
                .distinct()
                .flatMap(method -> Stream.of(method.getDeclaringClass().getSimpleName() + "#" + method.getName()))
                .forEach(name -> log.warn(
                        "[ApiScopeRegistry] @RequiredApiScope 선언이 외부 경로에 없다 — @ExternalApi 누락 : {}", name));
    }

    private record ScopeRule(String key, RequestMatcher matcher, ApiKeyScope scope) {
    }
}
