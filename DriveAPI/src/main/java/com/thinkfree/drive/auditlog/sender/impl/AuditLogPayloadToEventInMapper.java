package com.thinkfree.drive.auditlog.sender.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxmind.geoip2.record.Country;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.tenant.TenantNameResolver;
import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.userlog.service.IpLocationService;
import com.thinkfree.governance.sdk.model.Account;
import com.thinkfree.governance.sdk.model.Actor;
import com.thinkfree.governance.sdk.model.Change;
import com.thinkfree.governance.sdk.model.Context;
import com.thinkfree.governance.sdk.model.EventIn;
import com.thinkfree.governance.sdk.model.EventObject;
import com.thinkfree.governance.sdk.model.Origin;
import com.thinkfree.governance.sdk.model.Outcome;
import com.thinkfree.governance.sdk.model.Scope;
import com.thinkfree.governance.sdk.model.Target;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link AuditLogPayload}(TFD 감사 이벤트 1건)를 Governance {@link EventIn}으로 변환한다(TFD 연동 최종본 기준).
 * <p>
 * 매핑 요지(자세한 근거는 연동 문서):
 * <ul>
 *   <li>eventType: TFD eventName과 동일명. 단 LOGIN_SUCCESS/LOGIN_FAILED만 {@code LOGIN}으로 합침(성공/실패는 outcome.status).
 *       LOGOUT은 별개 eventType(로그인 조회에 안 섞임). 원본 이름은 sourceEventType에 보존.</li>
 *   <li>before/after: {@code change.before}/{@code change.after}. JSON 문자열은 객체로 파싱, 단일값은 문자열 그대로.
 *       단 공유 권한·범위 변경은 {@code change}가 아니라 {@code targets}가 담는다.</li>
 *   <li>eventCategory: 최상위 {@code sourceEventCategory}(서버 조회 필터가 읽는 자리). TFD 고유값은 {@code sourceMetadata.tfd}.</li>
 *   <li>sourceMetadata.tfd.countryCode/countryName: ip를 GeoIP mmdb로 조회해 판별되면 기록(추후 대비, 조회 미사용).
 *       사설 IP 등 판별 불가 시 생략.</li>
 *   <li>object.type: FILE/FOLDER/USER/SETTING(eventName·category로 도출), storage=TFD. USER면 email도 채운다.</li>
 *   <li>outcome.status: SUCCESS/FAIL(성공은 이름에 안 섞음).</li>
 * </ul>
 * schemaVersion·eventId는 SDK 클라이언트가 생성하므로 여기서 설정하지 않는다.
 */
public class AuditLogPayloadToEventInMapper {

    private static final String ORIGIN_SYSTEM = "TFD";
    private static final String ORIGIN_COMPONENT = "SERVER";
    private static final String STORAGE = "TFD";
    private static final String OBJECT_TYPE_USER = "USER";

    /** 공유 범위 스냅샷(before/after) 안에서 공유 종류를 담고 있는 키. 나머지 키는 권한이다. */
    private static final String SHARE_TYPE_KEY = "shareType";
    private static final String SHARE_TYPE_PUBLIC_LINK = "PUBLIC_LINK";
    private static final String SHARE_TYPE_ORG_LINK = "ORG_LINK";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String originInstanceId;
    private final TenantNameResolver tenantNameResolver;
    private final IpLocationService ipLocationService;

    public AuditLogPayloadToEventInMapper(String originInstanceId, TenantNameResolver tenantNameResolver,
                                           IpLocationService ipLocationService) {
        this.originInstanceId = originInstanceId;
        this.tenantNameResolver = tenantNameResolver;
        this.ipLocationService = ipLocationService;
    }

    /**
     * @return 변환된 EventIn. eventName이 없으면 {@code null}(sender가 skip).
     * @throws RuntimeException change 값 직렬화 등 변환 실패 시(해당 이벤트 전송 실패로 드러남)
     */
    public EventIn toEventIn(AuditLogPayload p) {
        EventName eventName = p.getEventName();
        if (eventName == null) {
            return null;
        }

        // 로그인 성공/실패는 eventType·sourceEventType 둘 다 LOGIN으로 통일(구분은 outcome.status).
        // 그 외 이벤트는 eventType == sourceEventType == 원본명. → 두 필드 값이 항상 동일.
        String resolvedType = resolveEventType(eventName);
        EventIn.Builder builder = EventIn.newBuilder()
                .setEventType(resolvedType)
                .setSourceEventType(resolvedType)
                .setActor(buildActor(p))
                .setOrigin(buildOrigin())
                .setOutcome(buildOutcome(p));

        if (p.getEventCategory() != null) {
            builder.setSourceEventCategory(p.getEventCategory().name());
        }

        if (p.getEventRegisterDate() != null) {
            builder.setOccurredAt(p.getEventRegisterDate().toString());
        }

        Scope scope = buildScope(p);
        if (scope != null) {
            builder.setScope(scope);
        }

        EventObject object = buildObject(p);
        if (object != null) {
            builder.setObject(object);
        }

        Map<String, Object> tfd = buildTfd(p);
        if (!tfd.isEmpty()) {
            builder.putSourceMetadata("tfd", tfd);
        }

        Target target = buildTarget(p);
        if (target != null) {
            builder.addTarget(target);
        }

        Context context = buildContext(p);
        if (context != null) {
            builder.setContext(context);
        }

        // 공유 권한·범위 변경은 targets가 before/after를 담는다. targets로 표현하지 못한 건만 change로 남긴다.
        if (target == null || !isShareEvent(p)) {
            Change change = buildChange(p);
            if (change != null) {
                builder.setChange(change);
            }
        }

        return builder.build();
    }

    // TFD eventName → eventType. 로그인 시도(성공/실패)는 LOGIN 하나로 통일하고 성공/실패는 outcome.status로 구분.
    // LOGOUT은 로그인과 별개 행위라 독립 eventType(거버넌스 분류 기준 — LOGIN 조회에 LOGOUT 미포함). 나머지는 동일명.
    // (eventType·sourceEventType 둘 다 이 값을 쓴다 — 원본명을 따로 보존하지 않음)
    private String resolveEventType(EventName eventName) {
        switch (eventName) {
            case LOGIN_SUCCESS:
            case LOGIN_FAILED:
                return "LOGIN";
            default:
                return eventName.name();
        }
    }

    private Actor buildActor(AuditLogPayload p) {
        Actor.Builder actor = Actor.newBuilder().setType("HUMAN");
        if (p.getUserSeq() != null) {
            actor.setId(String.valueOf(p.getUserSeq()));
        }
        setIfText(actor::setName, p.getUserName());
        setIfText(actor::setEmail, p.getUserId());
        return actor.build();
    }

    private Origin buildOrigin() {
        Origin.Builder origin = Origin.newBuilder()
                .setSystem(ORIGIN_SYSTEM)
                .setComponent(ORIGIN_COMPONENT);
        setIfText(origin::setInstanceId, originInstanceId);
        return origin.build();
    }

    // 성공을 이름에 섞지 않는다: status는 SUCCESS/FAIL만. (실패 사유코드는 TFD payload에 없어 생략)
    private Outcome buildOutcome(AuditLogPayload p) {
        boolean success = p.getOutcomeStatus() == null || "SUCCESS".equalsIgnoreCase(p.getOutcomeStatus());
        return Outcome.newBuilder()
                .setStatus(success ? "SUCCESS" : "FAIL")
                .build();
    }

    private Scope buildScope(AuditLogPayload p) {
        if (p.getTenantSeq() == null) {
            return null;
        }
        Scope.Builder scope = Scope.newBuilder()
                .setType("TENANT")
                .setId(String.valueOf(p.getTenantSeq()));
        setIfText(scope::setName, resolveTenantName(p));
        return scope.build();
    }

    /** 테넌트 이름: payload에 명시된 값(override) 우선, 없으면 tenantSeq로 조회(캐시). 요청/SYSTEM 경로 공통. */
    private String resolveTenantName(AuditLogPayload p) {
        return hasText(p.getTenantName())
                ? p.getTenantName()
                : tenantNameResolver.resolve(p.getTenantSeq());
    }

    // object.type을 eventName/category로 도출. 대상 정보(objectSeq/name/path)가 하나도 없으면 object 생략.
    private EventObject buildObject(AuditLogPayload p) {
        String type = resolveObjectType(p);
        if (type == null) {
            return null;
        }
        if (p.getObjectSeq() == null && !hasText(p.getObjectName()) && !hasText(p.getResourcePath())) {
            return null;
        }
        EventObject.Builder object = EventObject.newBuilder().setType(type).setStorage(STORAGE);
        if (p.getObjectSeq() != null) {
            object.setId(String.valueOf(p.getObjectSeq()));
        }
        setIfText(object::setName, p.getObjectName());
        setIfText(object::setPath, p.getResourcePath());
        if (OBJECT_TYPE_USER.equals(type)) {
            setIfText(object::setEmail, p.getObjectUserId());
        }
        return object.build();
    }

    private String resolveObjectType(AuditLogPayload p) {
        EventName eventName = p.getEventName();
        String name = eventName.name();
        if (name.startsWith("FOLDER_")) {
            return "FOLDER";
        }
        if (name.startsWith("FILE_")) {
            return "FILE";
        }
        if (eventName == EventName.ACCOUNT_LOCKED_STATUS_CHANGE) {
            return "USER";
        }
        EventCategory category = p.getEventCategory();
        if (category == EventCategory.USER_MANAGEMENT) {
            return "USER";
        }
        if (category == EventCategory.SETTING) {
            return "SETTING";
        }
        if (category == EventCategory.SHARE) {
            // 공유 대상 리소스(파일/폴더 구분 정보가 payload에 없어 FILE로 표기)
            return "FILE";
        }
        return null; // LOGIN 등 문서 없는 이벤트
    }

    /**
     * 대상자가 있으면 그 계정 1건, 없으면 공유 범위(링크 자체)를 대상으로 만든다. 둘 다 아니면 {@code null}.
     */
    private Target buildTarget(AuditLogPayload p) {
        boolean hasTargetUser = p.getTargetUserSeq() != null
                || hasText(p.getTargetUserId())
                || hasText(p.getTargetUserName());
        if (hasTargetUser) {
            return buildAccountTarget(p);
        }
        // 공유 범위(링크 자체)는 대상자와 별개로 항상 1건씩 기록된다 — SHARE_CREATE·SHARE_REMOVE·SHARE_PERMISSION_CHANGE 공통.
        return isShareEvent(p) ? buildScopeTarget(p) : null;
    }

    /**
     * 대상자 1명. targetTenant*가 있으면 외부 테넌트(TENANT/entity), 없으면 내부 사용자(USER).
     * 둘 다 selection=ACCOUNTS + accounts 1건이고, 그 계정의 권한 전후는 accounts[].permission에 담는다.
     */
    private Target buildAccountTarget(AuditLogPayload p) {
        Account.Builder account = Account.newBuilder();
        if (p.getTargetUserSeq() != null) {
            account.setId(String.valueOf(p.getTargetUserSeq()));
        }
        setIfText(account::setName, p.getTargetUserName());
        setIfText(account::setEmail, p.getTargetUserId());
        if (isShareEvent(p)) {
            Object permission = beforeAfter(parseJsonOrString(p.getBeforeValue()), parseJsonOrString(p.getAfterValue()));
            if (permission != null) {
                account.setPermission(permission);
            }
        }

        Target.Builder target;
        if (p.getTargetTenantSeq() != null) {
            target = Target.newBuilder()
                    .setType("TENANT")
                    .setSelection("ACCOUNTS")
                    .setEntityId(String.valueOf(p.getTargetTenantSeq()));
            setIfText(target::setEntityName, p.getTargetTenantName());
        } else {
            target = Target.newBuilder()
                    .setType("USER")
                    .setSelection("ACCOUNTS");
        }
        target.addAccount(account.build());
        return target.build();
    }

    /**
     * 대상자 없는 공유 범위 건(링크 생성·해제·범위 변경). before/after 스냅샷을 shareScope(공유 종류)와
     * permission(권한)으로 나눠 담는다.
     * <p>
     * selection은 남은 상태의 종류로 정한다 — after가 있으면 after, 해제처럼 after가 없으면 before를 본다.
     * PUBLIC_LINK는 ANYONE, ORG_LINK는 행위자 테넌트 전체(ORGANIZATION/ALL).
     * SPECIFIC_USERS는 링크 자체에 대상이 없고 실제 대상자는 대상자별 이벤트가 ACCOUNTS로 남기므로 여기서는
     * {@code null} — 호출부가 change로 남긴다.
     */
    private Target buildScopeTarget(AuditLogPayload p) {
        ObjectNode before = parseObjectNode(p.getBeforeValue());
        ObjectNode after = parseObjectNode(p.getAfterValue());

        String beforeShareType = textOrNull(before, SHARE_TYPE_KEY);
        String afterShareType = textOrNull(after, SHARE_TYPE_KEY);
        Target.Builder target = newScopeTarget(afterShareType != null ? afterShareType : beforeShareType, p);
        if (target == null) {
            return null;
        }

        Object shareScope = beforeAfter(beforeShareType, afterShareType);
        if (shareScope != null) {
            target.setShareScope(shareScope);
        }
        Object permission = beforeAfter(withoutShareType(before), withoutShareType(after));
        if (permission != null) {
            target.setPermission(permission);
        }
        return target.build();
    }

    private Target.Builder newScopeTarget(String shareType, AuditLogPayload p) {
        if (SHARE_TYPE_PUBLIC_LINK.equals(shareType)) {
            // 링크를 가진 누구나 — 대상을 특정하는 값이 없는 것이 정상이라 type·entity·accounts를 보내지 않는다.
            return Target.newBuilder().setSelection("ANYONE");
        }
        if (SHARE_TYPE_ORG_LINK.equals(shareType) && p.getTenantSeq() != null) {
            Target.Builder target = Target.newBuilder()
                    .setType("ORGANIZATION")
                    .setSelection("ALL")
                    .setEntityId(String.valueOf(p.getTenantSeq()));
            setIfText(target::setEntityName, resolveTenantName(p));
            return target;
        }
        return null;
    }

    /** 값이 있는 쪽만 담은 {before, after}. 둘 다 없으면 {@code null}. */
    private Map<String, Object> beforeAfter(Object before, Object after) {
        if (before == null && after == null) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        if (before != null) {
            value.put("before", before);
        }
        if (after != null) {
            value.put("after", after);
        }
        return value;
    }

    private ObjectNode parseObjectNode(String value) {
        Object parsed = parseJsonOrString(value);
        return parsed instanceof ObjectNode ? (ObjectNode) parsed : null;
    }

    private String textOrNull(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** 공유 종류를 뺀 나머지(권한) 스냅샷. 남는 값이 없으면 {@code null}. */
    private ObjectNode withoutShareType(ObjectNode node) {
        if (node == null) {
            return null;
        }
        ObjectNode permission = node.deepCopy();
        permission.remove(SHARE_TYPE_KEY);
        return permission.isEmpty() ? null : permission;
    }

    private boolean isShareEvent(AuditLogPayload p) {
        return p.getEventCategory() == EventCategory.SHARE;
    }

    private Context buildContext(AuditLogPayload p) {
        AccessMethod accessMethod = p.getAccessMethod();
        if (accessMethod == null && !hasText(p.getIpAddress())) {
            return null;
        }
        Context.Builder context = Context.newBuilder();
        if (accessMethod != null) {
            context.setChannel(accessMethod.name());
        }
        setIfText(context::setClientIp, p.getIpAddress());
        return context.build();
    }

    /**
     * before/after를 change로. JSON 문자열(SHARE 권한 등)은 객체/배열로 파싱, 단일값(설정/상태)은 문자열 그대로.
     * 둘 다 없으면 change를 만들지 않는다(null 반환).
     */
    private Change buildChange(AuditLogPayload p) {
        Object before = parseJsonOrString(p.getBeforeValue());
        Object after = parseJsonOrString(p.getAfterValue());
        if (before == null && after == null) {
            return null;
        }
        Change.Builder change = Change.newBuilder();
        if (before != null) {
            change.setBefore(before);
        }
        if (after != null) {
            change.setAfter(after);
        }
        return change.build();
    }

    // 값이 유효 JSON(객체/배열/스칼라)이면 파싱한 트리를, 아니면(예: "ALLOW_ALL","ACTIVE") 원문 문자열을 돌려준다.
    private Object parseJsonOrString(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return (node.isObject() || node.isArray()) ? node : value;
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * sourceMetadata.tfd — 표준 자리가 없는 TFD 고유값만. eventCategory는 최상위 sourceEventCategory로 올라갔다.
     * objectUserId는 예전엔 여기 담았지만, 대상이 계정(USER)이면 항상 object.email로 실리므로(현재 objectUserId를
     * 채우는 모든 호출 경로가 USER 타입) 더 이상 중복해서 담지 않는다.
     */
    private Map<String, Object> buildTfd(AuditLogPayload p) {
        Map<String, Object> tfd = new LinkedHashMap<>();
        putCountryIfResolvable(tfd, p.getIpAddress());
        return tfd;
    }

    /** ip → country(GeoIP mmdb). 조회에는 아직 쓰이지 않고 추후 대비 기록용. 사설 IP 등 판별 불가 시 생략. */
    private void putCountryIfResolvable(Map<String, Object> tfd, String ipAddress) {
        if (!hasText(ipAddress)) {
            return;
        }
        Country country = ipLocationService.getCountry(ipAddress);
        if (country == null || !hasText(country.getIsoCode())) {
            return;
        }
        tfd.put("countryCode", country.getIsoCode());
        tfd.put("countryName", country.getName());
    }

    private static void setIfText(Consumer<String> setter, String value) {
        if (hasText(value)) {
            setter.accept(value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
