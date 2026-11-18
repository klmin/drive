package com.thinkfree.drive.global.config.externalapi;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.security.ApiScopeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 외부로 열린 endpoint 집합과 요구 scope 가 {@code external-api-endpoints.txt} 목록과 일치하는지 검증.
 * <p>
 * 컨트롤러 소스가 아니라 <b>실제 등록된 핸들러 매핑</b>을 열거한다. {@code @ExternalApi} 는
 * 한 줄 추가로 보이지만 실제로 열리는 경로는 그렇지 않을 수 있어서다 —
 * 한 메서드에 path 가 여러 개면 전부 열리고, {@code @RequestMapping} 에 method 를 안 쓰면
 * 모든 HTTP 메서드가 열린다(목록에서는 {@code ANY}). diff 로는 드러나지 않는다.
 * <p>
 * 목록은 {@code /api/v1} 경로로 적고 여기서 {@code /api/public/v1} 로 치환해 대조한다 —
 * Confluence 문서와 그대로 대조하기 위해서다. 이미 {@code /api/public/v1} 로 시작하는 줄은
 * 미러링이 아니라 직접 등록된 경로라 치환하지 않는다.
 * <p>
 * 설정으로 켜고 끄는 컨트롤러(감사 로그 — {@code governance.audit.enabled})는 여기서 켜 둔다 —
 * 목록은 기능이 켜졌을 때 열리는 전체 집합이라, 실행 환경 값에 따라 결과가 달라지지 않게 하기 위해서다.
 */
@DisplayName("외부 개방 endpoint 커버리지")
@TestPropertySource(properties = "governance.audit.enabled=true")
class ExternalApiCoverageTest extends AbstractMvcTest {

    private static final String LIST_FILE = "external-api-endpoints.txt";
    private static final String INTERNAL = DriveConstants.API_V1_PREFIX;
    private static final String EXTERNAL = DriveConstants.API_PUBLIC_V1_PREFIX;

    /** method 조건이 없는 매핑 — 전 HTTP 메서드가 열린다 */
    private static final String ANY_METHOD = "ANY";

    /** 요구 scope 가 없다는 표기 — scope 검사 대상이 아니다 */

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApiScopeRegistry apiScopeRegistry;

    /** 목록 파일 한 줄 — {@code <HTTP method> <path> <scope>} */
    private record Row(String key, String scope) {
    }

    private List<Row> rows() throws IOException {
        List<Row> rows = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(LIST_FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                rows.add(new Row(parts[0] + " " + toExternal(parts[1]), parts[2]));
            }
        }
        return rows;
    }

    private static String toExternal(String path) {
        return path.startsWith(EXTERNAL) ? path : EXTERNAL + path.substring(INTERNAL.length());
    }

    /** 목록 파일이 선언한 외부 경로 */
    private Set<String> declared() throws IOException {
        return new TreeSet<>(rows().stream().map(Row::key).toList());
    }

    /**
     * 목록 파일에서 {@code @RequiredApiScope} 선언이 있어야 하는 행 — WRITE 뿐이다.
     * READ 는 체인 게이트(api:* 하나라도)가 겸하므로 선언하지 않고, NONE 은 검사 대상이 아니다.
     */
    private Map<String, ApiKeyScope> declaredScopes() throws IOException {
        Map<String, ApiKeyScope> scopes = new TreeMap<>();
        rows().stream()
                .filter(row -> ApiKeyScope.WRITE.name().equals(row.scope()))
                .forEach(row -> scopes.put(row.key(), ApiKeyScope.WRITE));
        return scopes;
    }

    /** 실제로 /api/public/v1 에 등록된 매핑 */
    private Set<String> registered() {
        Set<String> registered = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            var patterns = info.getPathPatternsCondition();
            if (patterns == null) {
                return;
            }
            Set<String> methods = new LinkedHashSet<>();
            info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
            if (methods.isEmpty()) {
                methods.add(ANY_METHOD);
            }
            patterns.getPatterns().stream()
                    .map(Object::toString)
                    .filter(pattern -> pattern.startsWith(EXTERNAL))
                    .forEach(pattern -> methods.forEach(method -> registered.add(method + " " + pattern)));
        });
        return registered;
    }

    @Test
    @DisplayName("목록에 없는 endpoint 가 외부로 열려 있지 않다")
    void noUndeclaredEndpointIsOpen() throws IOException {
        Set<String> undeclared = new TreeSet<>(registered());
        undeclared.removeAll(declared());

        assertTrue(undeclared.isEmpty(),
                "목록에 없는데 외부로 열린 endpoint 가 있다 — @ExternalApi 를 빼거나 "
                        + LIST_FILE + " 에 추가해야 한다: " + undeclared);
    }

    @Test
    @DisplayName("목록의 endpoint 가 전부 외부로 열려 있다")
    void allDeclaredEndpointsAreOpen() throws IOException {
        Set<String> missing = new TreeSet<>(declared());
        missing.removeAll(registered());

        assertTrue(missing.isEmpty(),
                "목록에 있는데 외부로 열리지 않은 endpoint 가 있다 — @ExternalApi 가 빠졌다: " + missing);
    }

    /** READ 행에 선언이 붙어 있어도 이 단언이 잡는다 — 기대값에 WRITE 만 들어 있기 때문 */
    @Test
    @DisplayName("목록의 WRITE 행과 @RequiredApiScope 선언이 일치한다")
    void declaredScopesMatchRegistry() throws IOException {
        assertEquals(declaredScopes(), new TreeMap<>(apiScopeRegistry.declaredRules()),
                "목록의 WRITE 행과 @RequiredApiScope 선언이 다르다 — 둘 중 하나가 빠졌거나 값이 어긋난다");
    }
}
