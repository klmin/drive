package com.thinkfree.drive.global.config;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.security.ExternalApiRoleRules;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OpenAPIDefinition(
        info = @Info(title = "Thinkfree Drive API",
                description = "Thinkfree Drive API reference",
                version = "v1"))
@Configuration
public class SwaggerConfig {

    /** 내부 문서 그룹 — /api-docs/drive, Swagger UI 의 기본 선택(groups-order ASC) */
    public static final String DRIVE_GROUP = "drive";
    /** 외부 공개 문서 그룹 — /api-docs/public, Swagger UI 의 문서 선택에도 이 이름으로 나온다 */
    public static final String PUBLIC_GROUP = "public";

    @Bean
    public OpenAPI openAPI() {
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("accessToken");

        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(new Components()
                        .addSecuritySchemes("accessToken",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP).scheme("Bearer").bearerFormat("JWT")));
    }

    /**
     * 내부 API 문서({@code /api/v1}). 경로 필터는 yml 의 {@code springdoc.paths-to-match} 에 두면 안 된다 —
     * springdoc 은 전역 값이 있으면 그룹의 pathsToMatch 를 무시하고 전역 값으로 거른다(2.8.17 확인).
     */
    @Bean
    public GroupedOpenApi driveApi() {
        return GroupedOpenApi.builder()
                .group(DRIVE_GROUP)
                .pathsToMatch(DriveConstants.API_V1_PREFIX + "/**")
                .build();
    }

    /**
     * 외부 공개 API 문서 — {@code @ExternalApi} 메서드는 {@code ExternalApiHandlerMapping} 이 {@code /api/public/v1} 경로를 하나 더
     * 등록하므로, 그 경로만 고르면 외부 개방 API 가 public 주소로 모인다. 슈퍼관리자 경로는 테넌트 밖 운영용이라 뺀다.
     * Office 세션 열기({@code /office/client-sessions})는 외부로 열려 있지만 공개 문서(Developers Management SDK 목록)에는 싣지 않는다
     * — 문서에서만 빠지고 호출은 그대로 된다.
     * {@code x-drive} 는 외부 호출자에게 의미 있는 값만 남긴다 — external 은 전부 true, anonymous 는 /api/v1 기준이라 뺀다.
     * 대신 operation 마다 {@code roles}(이 경로를 호출할 수 있는 API Key role)를 맨 앞에 넣는다 — 외부 체인 인가와 같은
     * {@link ExternalApiRoleRules} 에서 계산하므로 인가 규칙과 문서가 어긋나지 않는다.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group(PUBLIC_GROUP)
                .pathsToMatch(DriveConstants.API_PUBLIC_V1_PREFIX + "/**")
                .pathsToExclude(DriveConstants.API_PUBLIC_V1_PREFIX + "/super-admin/**",
                        DriveConstants.API_PUBLIC_V1_PREFIX + "/office/client-sessions")
                .addOpenApiCustomizer(openApi -> {
                    openApi.setServers(List.of());
                    openApi.getPaths().forEach((path, pathItem) ->
                            pathItem.readOperations().forEach(operation -> publicExtension(path, operation)));
                })
                .build();
    }

    /** 내부용 값(external·anonymous)을 빼고, 외부 체인 기준 호출 role 을 맨 앞에 넣는다 */
    private static void publicExtension(String path, Operation operation) {
        Map<String, Object> drive = new LinkedHashMap<>();
        drive.put("roles", ExternalApiRoleRules.rolesFor(path));
        if (operation.getExtensions() != null
                && operation.getExtensions().get(DriveOperationExtensionCustomizer.DRIVE_EXTENSION) instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> {
                if (!"external".equals(key) && !"anonymous".equals(key)) {
                    drive.put(String.valueOf(key), value);
                }
            });
        }
        operation.addExtension(DriveOperationExtensionCustomizer.DRIVE_EXTENSION, drive);
    }
}
