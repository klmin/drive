package com.thinkfree.drive.auditlog.sender.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.fixture.IpFixtures;
import com.thinkfree.drive.userlog.service.IpLocationService;
import com.thinkfree.governance.sdk.json.jackson.JacksonJsonCodec;
import com.thinkfree.governance.sdk.model.EventIn;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditLogPayload → EventIn 변환이 TFD 연동 최종본 규칙과 맞는지 검증한다(SDK codec으로 직렬화한 JSON 확인).
 * 검증 대상은 우리 매퍼가 채우는 값(eventType/object/change/sourceMetadata/targets 등)이다
 * — schemaVersion·eventId는 SDK 클라이언트가 채우므로 여기서 단언하지 않는다.
 */
class AuditLogPayloadToEventInMapperTest {

    // 리졸버는 seq→이름 fallback용. 이 테스트 payload는 tenantName을 명시하므로 override가 우선되어 호출되지 않는다.
    private final AuditLogPayloadToEventInMapper mapper =
            new AuditLogPayloadToEventInMapper("tfd-test-01", tenantSeq -> null, newIpLocationService());
    private final JacksonJsonCodec codec = new JacksonJsonCodec();
    private final ObjectMapper om = new ObjectMapper();

    // IpLocationService 생성자가 체크 예외(GeoIP mmdb 로드)를 던져 필드 초기화에서 바로 쓸 수 없어 래핑한다.
    private static IpLocationService newIpLocationService() {
        try {
            return new IpLocationService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode serialize(EventIn event) throws Exception {
        return om.readTree(codec.write(event));
    }

    @Test
    void shareCreate_externalTenant_maps_to_object_and_target() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_CREATE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("재무팀/영수증")
                .targetTenantSeq(81).targetTenantName("HANCOM")
                .targetUserSeq(7302).targetUserId("minji.kim@hancom.com").targetUserName("김민지")
                .afterValue("{\"permissions\":[\"VIEW\",\"DOWNLOAD\"]}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        // eventType은 동일명, 원본은 sourceEventType
        assertThat(json.path("eventType").asText()).isEqualTo("SHARE_CREATE");
        assertThat(json.path("sourceEventType").asText()).isEqualTo("SHARE_CREATE");
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-07-09T11:20:10.200Z");
        assertThat(json.path("actor").path("type").asText()).isEqualTo("HUMAN");
        assertThat(json.path("actor").path("id").asText()).isEqualTo("4821");
        assertThat(json.path("actor").path("email").asText()).isEqualTo("seojun.park@acme.example");
        assertThat(json.path("scope").path("id").asText()).isEqualTo("27");
        // resource → object, storage=TFD
        assertThat(json.path("object").path("type").asText()).isEqualTo("FILE");
        assertThat(json.path("object").path("id").asText()).isEqualTo("942");
        assertThat(json.path("object").path("storage").asText()).isEqualTo("TFD");
        // object.path는 resourcePath의 trailing slash를 제거해 싣는다 (재무팀/영수증/ → 재무팀/영수증)
        assertThat(json.path("object").path("path").asText()).isEqualTo("재무팀/영수증");
        assertThat(json.path("context").path("channel").asText()).isEqualTo("WEB");
        assertThat(json.path("outcome").path("status").asText()).isEqualTo("SUCCESS");

        // 외부 테넌트 대상 → TENANT/ACCOUNTS/entity+accounts
        JsonNode target = json.path("targets").path(0);
        assertThat(target.path("type").asText()).isEqualTo("TENANT");
        assertThat(target.path("selection").asText()).isEqualTo("ACCOUNTS");
        assertThat(target.path("entity").path("id").asText()).isEqualTo("81");
        assertThat(target.path("entity").path("name").asText()).isEqualTo("HANCOM");
        JsonNode account = target.path("accounts").path(0);
        assertThat(account.path("id").asText()).isEqualTo("7302");
        assertThat(account.path("email").asText()).isEqualTo("minji.kim@hancom.com");

        // 부여된 권한은 change가 아니라 accounts[].permission.after 에 실린다.
        assertThat(json.has("evidenceItems")).isFalse();
        assertThat(json.has("change")).isFalse();
        JsonNode after = account.path("permission").path("after");
        assertThat(after.path("permissions").isArray()).isTrue();
        assertThat(after.path("permissions").get(0).asText()).isEqualTo("VIEW");

        // 상위 분류는 최상위 sourceEventCategory (서버 조회 필터가 읽는 자리)
        assertThat(json.path("sourceEventCategory").asText()).isEqualTo("SHARE");
    }

    /** 링크 자체 건(대상자 없음)은 SHARE_CREATE로도 나온다 — 범위는 targets에 실린다. */
    @Test
    void shareCreate_linkLevel_publicLink_maps_to_ANYONE_target() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_CREATE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .afterValue("{\"shareType\":\"PUBLIC_LINK\",\"canView\":true,\"canEdit\":false}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        JsonNode target = json.path("targets").path(0);
        assertThat(target.path("selection").asText()).isEqualTo("ANYONE");
        assertThat(target.has("accounts")).isFalse();
        // 생성이라 after만 있고 before는 만들지 않는다.
        assertThat(target.path("shareScope").path("after").asText()).isEqualTo("PUBLIC_LINK");
        assertThat(target.path("shareScope").has("before")).isFalse();
        assertThat(target.path("permission").path("after").path("canView").asBoolean()).isTrue();
        assertThat(json.has("change")).isFalse();
    }

    /** 해제 건은 afterValue가 없다 — before의 shareType으로 selection을 정한다. */
    @Test
    void shareRemove_linkLevel_usesBeforeShareType() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:30:00.000Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_REMOVE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .beforeValue("{\"shareType\":\"ORG_LINK\",\"canView\":true}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        JsonNode target = json.path("targets").path(0);
        assertThat(target.path("type").asText()).isEqualTo("ORGANIZATION");
        assertThat(target.path("selection").asText()).isEqualTo("ALL");
        assertThat(target.path("entity").path("id").asText()).isEqualTo("27");
        assertThat(target.path("shareScope").path("before").asText()).isEqualTo("ORG_LINK");
        assertThat(target.path("shareScope").has("after")).isFalse();
        assertThat(json.has("change")).isFalse();
    }

    /** SPECIFIC_USERS 링크 행은 대상이 없어 targets로 표현하지 못하고 change로 남는다. */
    @Test
    void shareCreate_linkLevel_specificUsers_fallsBackToChange() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_CREATE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .afterValue("{\"shareType\":\"SPECIFIC_USERS\",\"canView\":true}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        assertThat(json.path("targets").size()).isZero();
        assertThat(json.path("change").path("after").path("shareType").asText()).isEqualTo("SPECIFIC_USERS");
    }

    @Test
    void login_maps_to_LOGIN_eventType_withoutObject() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T08:58:12.000Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("minji.kim@acme.example").userName("김민지")
                .eventCategory(EventCategory.LOGIN).eventName(EventName.LOGIN_SUCCESS)
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        EventIn event = mapper.toEventIn(payload);
        assertThat(event).isNotNull();
        JsonNode json = serialize(event);

        // 로그인 성공/실패는 eventType·sourceEventType 둘 다 LOGIN으로 통일(구분은 outcome.status).
        // LOGOUT은 별개 eventType이라 여기 안 묶임.
        assertThat(json.path("eventType").asText()).isEqualTo("LOGIN");
        assertThat(json.path("sourceEventType").asText()).isEqualTo("LOGIN");
        // 로그인은 문서(object)가 없음
        assertThat(json.has("object")).isFalse();
        assertThat(json.path("sourceEventCategory").asText()).isEqualTo("LOGIN");
        // 고유값이 없으면 sourceMetadata 자체를 만들지 않는다.
        assertThat(json.has("sourceMetadata")).isFalse();
    }

    @Test
    void scopeChange_toPublicLink_maps_to_ANYONE_target() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_PERMISSION_CHANGE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .beforeValue("{\"shareType\":\"SPECIFIC_USERS\",\"canView\":true,\"canEdit\":true}")
                .afterValue("{\"shareType\":\"PUBLIC_LINK\",\"canView\":true,\"canEdit\":false}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        // 링크를 가진 누구나 — type·entity·accounts를 보내지 않는다.
        JsonNode target = json.path("targets").path(0);
        assertThat(target.path("selection").asText()).isEqualTo("ANYONE");
        assertThat(target.has("type")).isFalse();
        assertThat(target.has("entity")).isFalse();
        assertThat(target.has("accounts")).isFalse();
        // 공유 종류는 shareScope, 권한은 permission으로 나뉘고 change는 만들지 않는다.
        assertThat(target.path("shareScope").path("before").asText()).isEqualTo("SPECIFIC_USERS");
        assertThat(target.path("shareScope").path("after").asText()).isEqualTo("PUBLIC_LINK");
        assertThat(target.path("permission").path("before").path("canEdit").asBoolean()).isTrue();
        assertThat(target.path("permission").path("after").path("canEdit").asBoolean()).isFalse();
        assertThat(target.path("permission").path("after").has("shareType")).isFalse();
        assertThat(json.has("change")).isFalse();
    }

    @Test
    void scopeChange_toOrgLink_maps_to_actorTenant_organization() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_PERMISSION_CHANGE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .afterValue("{\"shareType\":\"ORG_LINK\",\"canView\":true}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        JsonNode target = json.path("targets").path(0);
        assertThat(target.path("type").asText()).isEqualTo("ORGANIZATION");
        assertThat(target.path("selection").asText()).isEqualTo("ALL");
        assertThat(target.path("entity").path("id").asText()).isEqualTo("27");
        assertThat(target.path("entity").path("name").asText()).isEqualTo("ACME");
        assertThat(target.has("accounts")).isFalse();
    }

    /**
     * 특정 사용자만으로 되돌리는 건은 열거할 계정이 이 이벤트에 없어 targets 조합 규칙을 만족할 수 없다.
     * 그래서 targets 없이 change로 남긴다.
     */
    @Test
    void scopeChange_toSpecificUsers_fallsBackToChange() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T11:20:10.200Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("seojun.park@acme.example").userName("박서준")
                .eventCategory(EventCategory.SHARE).eventName(EventName.SHARE_PERMISSION_CHANGE)
                .objectSeq(942L).objectName("바우처.pdf").resourcePath("/재무팀")
                .beforeValue("{\"shareType\":\"PUBLIC_LINK\",\"canView\":true}")
                .afterValue("{\"shareType\":\"SPECIFIC_USERS\",\"canView\":true}")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        assertThat(json.path("targets").size()).isZero();
        assertThat(json.path("change").path("after").path("shareType").asText()).isEqualTo("SPECIFIC_USERS");
    }

    /** ip로 국가가 판별되면 sourceMetadata.tfd에 countryCode/countryName을 기록한다(조회 미사용, 추후 대비). */
    @Test
    void resolvableIp_putsCountry_onSourceMetadata() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T08:58:12.000Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("minji.kim@acme.example").userName("김민지")
                .eventCategory(EventCategory.LOGIN).eventName(EventName.LOGIN_SUCCESS)
                .accessMethod(AccessMethod.WEB).ipAddress(IpFixtures.IP_KR)
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        assertThat(json.path("sourceMetadata").path("tfd").path("countryCode").asText()).isEqualTo("KR");
        assertThat(json.path("sourceMetadata").path("tfd").path("countryName").asText()).isNotBlank();
    }

    /** 사설 IP 등 GeoIP로 국가를 판별할 수 없으면 country 값을 만들지 않는다. */
    @Test
    void unresolvableIp_omitsCountry_fromSourceMetadata() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T08:58:12.000Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("minji.kim@acme.example").userName("김민지")
                .eventCategory(EventCategory.LOGIN).eventName(EventName.LOGIN_SUCCESS)
                .accessMethod(AccessMethod.WEB).ipAddress(IpFixtures.IP_PRIVATE)
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        assertThat(json.has("sourceMetadata")).isFalse();
    }

    @Test
    void userEvent_putsAccountEmail_onObject() throws Exception {
        AuditLogPayload payload = AuditLogPayload.builder()
                .eventRegisterDate(Instant.parse("2026-07-09T09:00:00.000Z"))
                .tenantSeq(27).tenantName("ACME")
                .userSeq(4821).userId("admin@acme.example").userName("관리자")
                .eventCategory(EventCategory.USER_MANAGEMENT).eventName(EventName.USER_STATUS_CHANGE)
                .objectSeq(8842L).objectName("김철수").objectUserId("chulsoo.kim@acme.example")
                .accessMethod(AccessMethod.WEB).ipAddress("203.0.113.24")
                .outcomeStatus("SUCCESS")
                .build();

        JsonNode json = serialize(mapper.toEventIn(payload));

        assertThat(json.path("object").path("type").asText()).isEqualTo("USER");
        assertThat(json.path("object").path("email").asText()).isEqualTo("chulsoo.kim@acme.example");
        // object.email로 올라갔으므로 sourceMetadata에 중복하지 않는다.
        assertThat(json.has("sourceMetadata")).isFalse();
    }
}
