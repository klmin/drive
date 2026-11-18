package com.thinkfree.drive.global.config;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @ModelAttribute} 요청 객체를 필드별 파라미터로 펼친다 — springdoc 은 이 객체를 query 파라미터 하나({@code request: {...}})로 내보낸다.
 * <ul>
 *   <li>필드 이름이 URL 템플릿 변수({@code {resourceSeq}})와 같으면 path 파라미터(required), 아니면 query 파라미터(required 는 스키마 그대로)
 *       — Spring 이 query 값과 URI 변수를 이름으로 객체에 채우는 규칙과 같다.</li>
 *   <li>어느 파라미터로도 선언되지 않은 템플릿 변수(클래스 레벨 경로에만 있는 변수 등)는 문자열 path 파라미터로 추가한다 — OpenAPI 는 모든 템플릿 변수의 선언을 요구한다.</li>
 *   <li>타입이 없는 필드(자바 {@code Object}, 예: 커서 값 {@code cv})는 query 값이 문자열로 오므로 string 으로 둔다.</li>
 *   <li>펼친 뒤 아무 데서도 참조되지 않는 요청 객체 스키마는 지운다.</li>
 * </ul>
 * requestBody(JSON·multipart)·이미 개별로 나오는 {@code @RequestParam}·{@code @PathVariable} 은 건드리지 않는다.
 * springdoc 이 문서를 조립한 뒤 응답 직전에 돈다({@link GlobalOpenApiCustomizer} — 모든 그룹 문서에 적용).
 */
@Component
public class QueryObjectParameterCustomizer implements GlobalOpenApiCustomizer {

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{([^}/]+)}");
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents() == null || openApi.getComponents().getSchemas() == null
                ? Map.of() : openApi.getComponents().getSchemas();
        Set<String> flattened = new HashSet<>();
        openApi.getPaths().forEach((path, pathItem) -> {
            Set<String> variables = templateVariables(path);
            pathItem.readOperations().forEach(operation -> flatten(operation, variables, schemas, flattened));
        });
        removeUnreferenced(openApi, flattened);
    }

    private static void flatten(Operation operation, Set<String> variables, Map<String, Schema> schemas, Set<String> flattened) {
        List<Parameter> parameters = operation.getParameters() == null ? List.of() : operation.getParameters();
        List<Parameter> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Parameter parameter : parameters) {
            Schema<?> object = "query".equals(parameter.getIn()) ? objectSchema(parameter.getSchema(), schemas) : null;
            if (object == null) {
                add(result, names, parameter);
                continue;
            }
            if (parameter.getSchema().get$ref() != null) {
                flattened.add(schemaName(parameter.getSchema().get$ref()));
            }
            List<String> required = object.getRequired() == null ? List.of() : object.getRequired();
            object.getProperties().forEach((name, property) -> {
                boolean inPath = variables.contains(name);
                add(result, names, new Parameter()
                        .name(name)
                        .in(inPath ? "path" : "query")
                        .required(inPath || required.contains(name))
                        .description(property.getDescription())
                        .schema(hasType(property) ? property : stringSchema().title(property.getTitle()).description(property.getDescription())));
            });
        }
        for (String variable : variables) {
            if (!names.contains("path:" + variable)) {
                add(result, names, new Parameter().name(variable).in("path").required(true).schema(stringSchema()));
            }
        }
        operation.setParameters(result.isEmpty() ? null : result);
    }

    /** 같은 위치·이름이 이미 있으면 먼저 나온 것을 둔다 */
    private static void add(List<Parameter> result, Set<String> names, Parameter parameter) {
        if (names.add(parameter.getIn() + ":" + parameter.getName())) {
            result.add(parameter);
        }
    }

    /** 필드를 가진 객체 스키마면 반환 — enum·단일 값 스키마($ref 포함)는 null */
    private static Schema<?> objectSchema(Schema<?> schema, Map<String, Schema> schemas) {
        if (schema == null) {
            return null;
        }
        Schema<?> resolved = schema.get$ref() == null ? schema : schemas.get(schemaName(schema.get$ref()));
        return resolved != null && resolved.getProperties() != null && !resolved.getProperties().isEmpty() ? resolved : null;
    }

    private static boolean hasType(Schema<?> schema) {
        return schema.get$ref() != null || schema.getType() != null || (schema.getTypes() != null && !schema.getTypes().isEmpty())
                || schema.getAllOf() != null || schema.getOneOf() != null || schema.getAnyOf() != null;
    }

    private static Schema<String> stringSchema() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setTypes(new LinkedHashSet<>(Set.of("string")));
        return schema;
    }

    private static Set<String> templateVariables(String path) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = TEMPLATE_VARIABLE.matcher(path);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private static String schemaName(String ref) {
        return ref.startsWith(SCHEMA_REF_PREFIX) ? ref.substring(SCHEMA_REF_PREFIX.length()) : ref;
    }

    /** 펼친 요청 객체 스키마 중 문서 어디에서도 참조되지 않는 것을 지운다(requestBody 등에서 함께 쓰이면 남는다) */
    private static void removeUnreferenced(OpenAPI openApi, Set<String> flattened) {
        if (flattened.isEmpty() || openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        String json;
        try {
            json = Json31.mapper().writeValueAsString(openApi);
        } catch (Exception e) {
            return; // 정리는 선택 — 직렬화가 안 되면 스키마를 남긴다
        }
        for (String name : flattened) {
            if (!json.contains("\"" + SCHEMA_REF_PREFIX + name + "\"")) {
                openApi.getComponents().getSchemas().remove(name);
            }
        }
    }
}
