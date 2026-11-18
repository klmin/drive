package com.thinkfree.drive.global.config;

import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.tenant.policy.DefaultTenantPolicy;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import com.thinkfree.drive.tenant.policy.TenantRestrictionCondition;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OpenAPI 문서의 operation 마다 {@code x-drive} 확장 필드를 붙인다 — 문서를 읽는 쪽(Developers 플레이그라운드 등)이 쓴다.
 * <ul>
 *   <li>{@code external}: {@code @ExternalApi} 여부 — true 면 {@code /api/public/v1} 로도 열린다</li>
 *   <li>{@code anonymous}: {@code @AnonymousAccessible} 여부({@code /api/v1} 기준)</li>
 *   <li>{@code apiScope}: {@code @RequiredApiScope} 값(예: api:write) — 선언이 있을 때만</li>
 *   <li>{@code restrictedWhen}: {@code @TenantRestriction} + 컨트롤러 메서드의 {@code @TenantFeatureRequired}
 *       — {@code [{tenantType?, userMode?, protectedAccount?}]}, 이 조건이면 거부된다.
 *       객체 안은 AND, 배열은 OR, 빠진 키는 상관없음. 받는 쪽은 자기 테넌트가 하나라도 맞으면 실행을 막는다(Limited). 선언이 있을 때만.</li>
 *   <li>{@code maskedWhen}: {@code @TenantRestriction} 의 마스킹 기능(*_INFO_VIEW) — 형식은 restrictedWhen 과 같고, 이 조건이면
 *       요청은 성공하되 응답의 일부 값이 가려진다. 받는 쪽은 표시만 하고 실행은 막지 않는다. 선언이 있을 때만.</li>
 * </ul>
 * 같은 자리에서 파라미터의 {@link ParameterDoc} 을 문서의 {@code parameters[].description}·{@code schema.title} 로 더한다.
 * {@code external}·{@code anonymous} 는 내부 문서용이다 — public 그룹 문서({@link SwaggerConfig#publicApi()})는 이 둘을 빼고,
 * 대신 경로 기준 {@code roles}(호출 가능한 API Key role, {@code ExternalApiRoleRules})를 넣는다.
 * 그룹 문서에도 붙도록 {@link GlobalOperationCustomizer} 로 둔다(일반 OperationCustomizer 빈은 기본 문서에만 적용된다).
 * 값은 컨트롤러 메서드의 어노테이션에서 읽으므로 코드와 문서가 어긋나지 않는다. API 동작에는 영향이 없다.
 */
@Component
public class DriveOperationExtensionCustomizer implements GlobalOperationCustomizer {

    /** operation 확장 필드 이름 — OpenAPI 확장은 x- 로 시작해야 한다 */
    public static final String DRIVE_EXTENSION = "x-drive";
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Map<String, Object> drive = new LinkedHashMap<>();
        drive.put("external", handlerMethod.hasMethodAnnotation(ExternalApi.class));
        drive.put("anonymous", handlerMethod.hasMethodAnnotation(AnonymousAccessible.class));
        RequiredApiScope scope = handlerMethod.getMethodAnnotation(RequiredApiScope.class);
        if (scope != null) {
            drive.put("apiScope", scope.value().getValue());
        }
        TenantRestriction restriction = handlerMethod.getMethodAnnotation(TenantRestriction.class);
        TenantFeatureRequired required = handlerMethod.getMethodAnnotation(TenantFeatureRequired.class);
        if (restriction != null || required != null) {
            putRestriction(drive, restriction, required);
        }
        operation.addExtension(DRIVE_EXTENSION, drive);
        applyParameterDocs(operation, handlerMethod);
        return operation;
    }

    /**
     * {@link ParameterDoc} 을 springdoc 이 만든 파라미터에 더한다 — description 과 스키마 title 만, 타입·형식은 그대로 둔다.
     * multipart 의 {@code @RequestParam MultipartFile} 처럼 본문 필드로 나간 파라미터는 본문 스키마의 같은 이름 필드에 더한다.
     */
    private static void applyParameterDocs(Operation operation, HandlerMethod handlerMethod) {
        for (MethodParameter methodParameter : handlerMethod.getMethodParameters()) {
            ParameterDoc doc = methodParameter.getParameterAnnotation(ParameterDoc.class);
            if (doc == null) {
                continue;
            }
            String name = boundName(methodParameter);
            if (operation.getParameters() != null) {
                operation.getParameters().stream().filter(parameter -> name.equals(parameter.getName())).forEach(parameter -> {
                    if (!doc.description().isEmpty()) {
                        parameter.setDescription(doc.description());
                    }
                    if (parameter.getSchema() != null) {
                        applyTitle(parameter.getSchema(), doc);
                    }
                });
            }
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                operation.getRequestBody().getContent().values().stream()
                        .map(mediaType -> mediaType.getSchema() == null || mediaType.getSchema().getProperties() == null
                                ? null : (Schema<?>) mediaType.getSchema().getProperties().get(name))
                        .filter(Objects::nonNull)
                        .forEach(property -> {
                            applyTitle(property, doc);
                            if (!doc.description().isEmpty()) {
                                property.setDescription(doc.description());
                            }
                        });
            }
        }
    }

    private static void applyTitle(Schema<?> schema, ParameterDoc doc) {
        if (!doc.title().isEmpty()) {
            schema.setTitle(doc.title());
        }
    }

    /** {@code @PathVariable}·{@code @RequestParam} 의 이름, 없으면 자바 파라미터 이름 */
    private static String boundName(MethodParameter methodParameter) {
        PathVariable path = methodParameter.getParameterAnnotation(PathVariable.class);
        RequestParam query = methodParameter.getParameterAnnotation(RequestParam.class);
        String declared = path != null ? (path.name().isEmpty() ? path.value() : path.name())
                : query != null ? (query.name().isEmpty() ? query.value() : query.name()) : "";
        if (!declared.isEmpty()) {
            return declared;
        }
        methodParameter.initParameterNameDiscovery(PARAMETER_NAMES);
        return String.valueOf(methodParameter.getParameterName());
    }

    /**
     * 기능은 {@code @TenantRestriction} 과 컨트롤러 메서드의 {@code @TenantFeatureRequired}(판정)를 합친다 — 판정이 컨트롤러에 있으면
     * 문서용 선언을 따로 달지 않아도 된다(UseCase 에 있는 판정은 보이지 않으므로 그때는 컨트롤러에 {@code @TenantRestriction}).
     * 거부 기능·보호 계정 판정은 restrictedWhen, 마스킹 기능은 maskedWhen — 분류는 {@link DefaultTenantPolicy#isMasked}.
     */
    private static void putRestriction(Map<String, Object> drive, TenantRestriction restriction, TenantFeatureRequired required) {
        Set<TenantFeature> features = new LinkedHashSet<>();
        if (restriction != null) {
            features.addAll(Arrays.asList(restriction.value()));
        }
        if (required != null) {
            features.add(required.value());
        }
        Set<TenantRestrictionCondition> rejected = new LinkedHashSet<>();
        Set<TenantRestrictionCondition> masked = new LinkedHashSet<>();
        for (TenantFeature feature : features) {
            (DefaultTenantPolicy.isMasked(feature) ? masked : rejected).addAll(DefaultTenantPolicy.restrictedWhen(feature));
        }
        if (restriction != null) {
            Arrays.stream(restriction.protectedAccount()).map(DefaultTenantPolicy::restrictedWhen).forEach(rejected::addAll);
        }
        Assert.state(!rejected.isEmpty() || !masked.isEmpty(), () -> "막히는 조건이 없는 테넌트 제한 선언: " + features);
        if (!rejected.isEmpty()) {
            drive.put("restrictedWhen", simplify(rejected));
        }
        if (!masked.isEmpty()) {
            drive.put("maskedWhen", simplify(masked));
        }
    }

    /**
     * 조건 목록(OR)에서 같은 조건과 더 넓은 조건에 포함되는 조건은 뺀다
     * — 예: {SYSTEM_MANAGED} 가 있으면 {SYSTEM_MANAGED, protectedAccount} 는 뺀다.
     */
    private static List<Map<String, Object>> simplify(Set<TenantRestrictionCondition> conditions) {
        return conditions.stream()
                .filter(c -> conditions.stream().noneMatch(wider -> !wider.equals(c) && covers(wider, c)))
                .map(TenantRestrictionCondition::toMap)
                .toList();
    }

    /** wider 가 맞는 테넌트·계정이 narrower 가 맞는 경우를 모두 포함하면 true */
    private static boolean covers(TenantRestrictionCondition wider, TenantRestrictionCondition narrower) {
        return (wider.tenantType() == null || wider.tenantType() == narrower.tenantType())
                && (wider.userMode() == null || wider.userMode() == narrower.userMode())
                && (!wider.protectedAccount() || narrower.protectedAccount());
    }
}
