package com.thinkfree.drive.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * springdoc(2.8.17, OpenAPI 3.1)이 {@code @Schema(title = ..., description = ...)} 를 단 필드마다 {@code default: ""} 를 붙이는 것을 지운다
 * — 정수 필드에도 붙어 문서가 틀리고, 문서를 읽는 쪽(Developers 폼)이 빈 기본값으로 오해할 수 있다.
 * 빈 문자열 기본값은 의도해서 쓰는 곳이 없으므로 컴포넌트 스키마·파라미터·요청 본문에서 모두 뺀다.
 * 모든 그룹 문서에 적용되도록 {@link GlobalOpenApiCustomizer} 로 둔다.
 */
@Component
public class EmptyDefaultCleanupCustomizer implements GlobalOpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            openApi.getComponents().getSchemas().values().forEach(schema -> clean(schema, visited));
        }
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            if (operation.getParameters() != null) {
                operation.getParameters().stream().map(Parameter::getSchema).forEach(schema -> clean(schema, visited));
            }
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                operation.getRequestBody().getContent().values().stream().map(MediaType::getSchema).forEach(schema -> clean(schema, visited));
            }
        }));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clean(Schema schema, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        // 클래스에 단 @Schema 는 기본값을 MissingNode 로 남겨 default: null 이 된다
        if ("".equals(schema.getDefault()) || schema.getDefault() instanceof JsonNode node && node.isMissingNode()) {
            schema.setDefault(null);
            // setDefault(null) 은 "기본값 지정됨" 표시를 남겨 3.1 직렬화가 default: null 을 쓴다 — 표시까지 지운다
            schema.setDefaultSetFlag(false);
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(property -> clean(property, visited));
        }
        clean(schema.getItems(), visited);
        for (List<Schema> composed : new List[]{schema.getAllOf(), schema.getOneOf(), schema.getAnyOf()}) {
            if (composed != null) {
                composed.forEach(part -> clean(part, visited));
            }
        }
        if (schema.getAdditionalProperties() instanceof Schema additional) {
            clean(additional, visited);
        }
        // @ArraySchema 는 3.1 문서에서 지정하지 않은 contains·unevaluatedItems 를 {default: ""} 로 만든다
        // — 빈 기본값을 지우면 아무 제약도 없는 스키마만 남으므로 항목 자체를 뺀다(contains:{} 는 "항목 1개 이상" 으로 읽힌다).
        clean(schema.getContains(), visited);
        if (isUnconstrained(schema.getContains())) {
            schema.setContains(null);
        }
        clean(schema.getUnevaluatedItems(), visited);
        if (isUnconstrained(schema.getUnevaluatedItems())) {
            schema.setUnevaluatedItems(null);
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean isUnconstrained(Schema schema) {
        return schema != null && schema.getDefault() == null && schema.get$ref() == null && schema.getType() == null
                && (schema.getTypes() == null || schema.getTypes().isEmpty()) && schema.getProperties() == null
                && schema.getItems() == null && schema.getEnum() == null && schema.getConst() == null
                && schema.getAllOf() == null && schema.getOneOf() == null && schema.getAnyOf() == null && schema.getNot() == null
                && schema.getFormat() == null && schema.getPattern() == null && schema.getTitle() == null && schema.getDescription() == null;
    }
}
