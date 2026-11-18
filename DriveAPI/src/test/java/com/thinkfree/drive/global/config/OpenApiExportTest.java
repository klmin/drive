package com.thinkfree.drive.global.config;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import com.thinkfree.drive.global.config.externalapi.ExternalApiConfig;
import com.thinkfree.drive.global.security.ExternalApiRoleRules;
import com.thinkfree.drive.global.security.ExternalTestController;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 문서를 JSON 파일로 내보낸다 — DB·Redis 등에 붙지 않고 컨트롤러 스캔만으로, springdoc 이 만든 문서를 내용 가공 없이 쓴다(파일 포맷만 {@link #format} 으로 고정).
 * <ul>
 *   <li>{@code drive-api.json}: drive 그룹 문서 {@code /api-docs/drive}({@code /api/v1} 경로, {@link SwaggerConfig#driveApi()})</li>
 *   <li>{@code drive-public-api.json}: public 그룹 문서 {@code /api-docs/public}({@code /api/public/v1} 경로, {@link SwaggerConfig#publicApi()})
 *       — Developers 플레이그라운드가 그대로 가져다 쓴다</li>
 * </ul>
 * {@code /api/public/v1} 경로는 {@link ExternalApiConfig} 의 핸들러 매핑이 등록하므로 함께 올린다.
 * 웹 계층만 띄운다({@code @WebMvcTest}) — 컨트롤러·웹 컴포넌트가 요구하는 서비스 등은 빈이 없으면 Mockito mock 으로 채운다({@link AutoMock}).
 * 문서는 컨트롤러 시그니처·어노테이션만으로 만들어지므로 mock 이어도 결과가 같다.
 * <p>
 * 평소 테스트 실행에서는 건너뛴다({@code openapi.export=true} 일 때만 돈다). 실행: {@code mvn -f DriveAPI/pom.xml -Popenapi-export test}
 * (프로필이 프로퍼티·대상 테스트·프런트 빌드 생략을 넣는다).
 * 파일은 기본 리포 루트의 {@code docs/openapi/} 에 쓴다. 경로는 테스트 작업 디렉터리(DriveAPI 모듈) 기준이며 {@code -Dopenapi.export.dir} 로 바꿀 수 있다.
 */
@DisplayName("OpenAPI 문서 내보내기")
@EnabledIfSystemProperty(named = "openapi.export", matches = "true")
// ExternalTestController 는 테스트 소스의 고정 경로(/api/public/v1/test/**) 컨트롤러라 문서에서 뺀다
// show-actuator 는 운영 yml 값과 무관하게 끈다 — 계약 문서에 actuator 경로는 넣지 않고, @WebMvcTest 에는 관리 포트 컨텍스트가 없어 켜면 springdoc 이 NPE(500)
@WebMvcTest(properties = "springdoc.show-actuator=false", excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ExternalTestController.class))
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocSecurityConfiguration.class,
        SpringDocSpecPropertiesConfiguration.class, SpringDocWebMvcConfiguration.class, MultipleOpenApiSupportConfiguration.class})
@Import({SwaggerConfig.class, DriveOperationExtensionCustomizer.class, QueryObjectParameterCustomizer.class, EmptyDefaultCleanupCustomizer.class, ExternalApiConfig.class})
@ContextConfiguration(initializers = OpenApiExportTest.AutoMock.class)
class OpenApiExportTest {

    /** DriveAPI 모듈에서 실행되므로 한 단계 위가 리포 루트다 */
    private static final String DEFAULT_DIR = "../docs/openapi";
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{([^}/]+)}");
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("drive·public 그룹 문서를 각각 파일로 쓴다")
    void export() throws Exception {
        Path dir = Path.of(System.getProperty("openapi.export.dir", DEFAULT_DIR));
        Files.createDirectories(dir);
        export("/api-docs/" + SwaggerConfig.DRIVE_GROUP, dir.resolve("drive-api.json"));
        export("/api-docs/" + SwaggerConfig.PUBLIC_GROUP, dir.resolve("drive-public-api.json"));
    }

    private void export(String url, Path file) throws Exception {
        String json = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode doc = objectMapper.readTree(json);
        assertEquals("3.1.0", doc.path("openapi").asText(), url);
        assertTrue(doc.path("paths").size() > 0, url + " paths 가 비어 있다");
        assertEquals(List.of(), parameterViolations(doc), url + " 파라미터 규칙 위반");
        // springdoc 이 @Schema 필드에 붙이는 빈 기본값은 EmptyDefaultCleanupCustomizer 가 지운다
        assertTrue(!Pattern.compile("\"default\"\\s*:\\s*(\"\"|null)").matcher(json).find(), url + " 빈 문자열·null default 가 남아 있다");
        if (url.endsWith("/" + SwaggerConfig.PUBLIC_GROUP)) {
            assertEquals(List.of(), roleViolations(doc), url + " x-drive.roles 누락·불일치");
        }
        Files.writeString(file, format(json), StandardCharsets.UTF_8);
    }

    /**
     * 공개 문서의 모든 operation 에 {@code x-drive.roles} 가 있고, 외부 체인 인가 규칙({@link ExternalApiRoleRules})과 같은지 —
     * Developers 플레이그라운드가 이 값으로 User·Admin 탭을 나누므로 빠지면 안 된다. 공개 문서에는 USER·ADMIN 만 나온다.
     */
    private static List<String> roleViolations(JsonNode doc) {
        List<String> violations = new ArrayList<>();
        doc.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            JsonNode roles = operation.getValue().path("x-drive").path("roles");
            List<String> actual = new ArrayList<>();
            roles.forEach(role -> actual.add(role.asText()));
            String where = operation.getKey().toUpperCase() + " " + path.getKey();
            if (actual.isEmpty()) {
                violations.add(where + " roles 없음");
            } else if (!actual.equals(ExternalApiRoleRules.rolesFor(path.getKey()))) {
                violations.add(where + " roles " + actual + " ≠ 인가 규칙");
            } else if (!List.of(ExternalApiRoleRules.USER, ExternalApiRoleRules.ADMIN).containsAll(actual)) {
                violations.add(where + " 공개 문서에 나올 수 없는 role " + actual);
            }
        }));
        return violations;
    }

    /**
     * 파일 포맷만 고정한다 — 2칸 들여쓰기, {@code "key": value}, 배열 항목마다 한 줄, 빈 배열·객체는 {@code []}·{@code {}}, LF, 끝 줄바꿈.
     * springdoc 기본 출력({@code "key" : value}, {@code [ "a" ]}, OS 줄바꿈)은 OS 마다 diff 가 달라지고 읽기 어렵다.
     * 내용은 바꾸지 않는다 — 앱 ObjectMapper 설정과 무관한 별도 매퍼로, 숫자는 정밀도 그대로(BigDecimal·정확한 소수) 다시 쓴다.
     */
    private static String format(String json) throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
                .build();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        Separators separators = Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator("");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(separators)
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
        return mapper.writer(printer).writeValueAsString(mapper.readTree(json)) + "\n";
    }

    /**
     * {@link QueryObjectParameterCustomizer} 가 지키는 규칙 — URL 템플릿 변수는 모두 path 파라미터로 선언,
     * 객체 하나짜리 query 파라미터 없음, 인증 주체(@AuthenticationPrincipal) 필드가 파라미터로 새지 않음.
     */
    private static List<String> parameterViolations(JsonNode doc) {
        JsonNode schemas = doc.path("components").path("schemas");
        List<String> violations = new ArrayList<>();
        doc.path("paths").properties().forEach(path -> path.getValue().properties().forEach(entry -> {
            JsonNode parameters = entry.getValue().path("parameters");
            String operation = entry.getKey().toUpperCase() + " " + path.getKey();
            Set<String> declaredPath = new HashSet<>();
            parameters.forEach(parameter -> {
                String name = parameter.path("name").asText();
                if ("path".equals(parameter.path("in").asText())) {
                    declaredPath.add(name);
                }
                if (name.startsWith("user.")) {
                    violations.add(operation + " 인증 주체 필드 노출: " + name);
                }
                JsonNode schema = parameter.path("schema");
                if (schema.has("$ref")) {
                    schema = schemas.path(schema.path("$ref").asText().substring("#/components/schemas/".length()));
                }
                if ("query".equals(parameter.path("in").asText()) && schema.path("properties").size() > 0) {
                    violations.add(operation + " 객체 query 파라미터: " + name);
                }
            });
            Matcher variable = TEMPLATE_VARIABLE.matcher(path.getKey());
            while (variable.find()) {
                if (!declaredPath.contains(variable.group(1))) {
                    violations.add(operation + " path 변수 선언 누락: " + variable.group(1));
                }
            }
        }));
        return violations;
    }

    /**
     * 빈이 없는 의존성을 Mockito mock 으로 채운다 — 컨트롤러·필터 등 웹 컴포넌트를 서비스·DB 없이 만들기 위해.
     * 값 주입(@Value)·컬렉션·스프링 인프라 타입(ApplicationContext·WebApplicationContext 등)은 건드리지 않는다.
     */
    static class AutoMock implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
            beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver() {
                @Override
                public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
                    Object lazy = super.getLazyResolutionProxyIfNecessary(descriptor, beanName);
                    if (lazy != null) {
                        return lazy;
                    }
                    Class<?> type = descriptor.getDependencyType();
                    if (!isMockable(type) || getSuggestedValue(descriptor) != null
                            || beanFactory.getBeanNamesForType(type, true, false).length > 0) {
                        return null;
                    }
                    return Mockito.mock(type);
                }
            });
        }

        private static boolean isMockable(Class<?> type) {
            if (type.isPrimitive() || type.isArray() || type == String.class || type == Boolean.class
                    || Number.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)
                    || Map.class.isAssignableFrom(type) || type == Optional.class || ObjectFactory.class.isAssignableFrom(type)) {
                return false;
            }
            String name = type.getName();
            return !(name.startsWith("org.springframework.context.") || name.startsWith("org.springframework.beans.")
                    || name.startsWith("org.springframework.core.") || name.startsWith("org.springframework.web.")
                    || name.startsWith("org.springframework.test.") || name.startsWith("jakarta.servlet."));
        }
    }
}
