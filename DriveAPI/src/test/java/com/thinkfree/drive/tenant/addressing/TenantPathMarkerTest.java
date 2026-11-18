package com.thinkfree.drive.tenant.addressing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantPathMarker — /t/{slug} 마커 상수·슬러그 추출")
class TenantPathMarkerTest {

    @Test
    @DisplayName("상수: SEGMENT=t, PREFIX=/t — 프론트 TENANT_PATH_MARKER 와 값이 같아야 한다")
    void constants() {
        assertThat(TenantPathMarker.SEGMENT).isEqualTo("t");
        assertThat(TenantPathMarker.PREFIX).isEqualTo("/t");
    }

    @Test
    @DisplayName("prefixOf: /t/{slug} (끝 슬래시 없음)")
    void prefixOf() {
        assertThat(TenantPathMarker.prefixOf("acme")).isEqualTo("/t/acme");
        assertThat(TenantPathMarker.prefixOf("default-add")).isEqualTo("/t/default-add");
    }

    @ParameterizedTest
    @CsvSource({
            "/t/acme/api/v1/users/me, acme",
            "/t/acme,                 acme",
            "/t/acme/,                acme",
            "/t/default-add/mcp,      default-add",
            "/t/a.b/x,                a.b",
            // 형식 검증은 호출자 몫 — 여기서는 세그먼트를 그대로 돌려준다
            "/t/INVALID_x/api,        INVALID_x",
            // 슬러그 이름이 마커·루트 라우트와 같아도 그냥 슬러그
            "/t/t/api,                t",
            "/t/drive/drive/,         drive",
            // 선행 슬래시 없는 경로도 세그먼트 기준으로 같게 본다(서블릿 경로는 항상 / 로 시작하므로 실전에서는 안 나옴)
            "t/acme/api,              acme"})
    @DisplayName("slugOf: 첫 세그먼트가 마커면 둘째 세그먼트(슬러그)를 그대로")
    void slugOfMarker(String path, String expected) {
        assertThat(TenantPathMarker.slugOf(path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/t", "/t/", "/t//x"})
    @DisplayName("slugOf: 마커는 있는데 뒤가 비면 빈 문자열 (형식 검증에서 NOT_FOUND 로 걸러짐)")
    void slugOfMarkerWithoutSlug(String path) {
        assertThat(TenantPathMarker.slugOf(path)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/", "/api/v1/users/me", "/mcp", "/_next/static/x.js", "/drive/",
            // 구 문법·유사 마커(대소문자 구분·부분 일치 없음)
            "/acme/api/v1/x", "/tt/acme/x", "/T/acme/x", "/t-x/acme",
            // RFC 삽입형은 slugOf 가 아니라 slugOfWellKnownInsertion 의 몫
            "/.well-known/oauth-authorization-server/t/acme"})
    @DisplayName("slugOf: 첫 세그먼트가 마커가 아니면 null (루트 요청)")
    void slugOfNoMarker(String path) {
        assertThat(TenantPathMarker.slugOf(path)).isNull();
    }

    @Test
    @DisplayName("상수: WELL_KNOWN_SEGMENT=.well-known")
    void wellKnownConstant() {
        assertThat(TenantPathMarker.WELL_KNOWN_SEGMENT).isEqualTo(".well-known");
    }

    @ParameterizedTest
    @CsvSource({
            "/.well-known/oauth-authorization-server/t/acme,        acme",
            "/.well-known/oauth-authorization-server/t/acme/,       acme",
            "/.well-known/openid-configuration/t/default-add,       default-add",
            "/.well-known/oauth-protected-resource/t/acme/mcp,      acme",
            // 형식 검증은 호출자 몫
            "/.well-known/oauth-authorization-server/t/INVALID_x,   INVALID_x"})
    @DisplayName("slugOfWellKnownInsertion: /.well-known/{doc}/t/{slug} 의 넷째 세그먼트")
    void slugOfWellKnownInsertion(String path, String expected) {
        assertThat(TenantPathMarker.slugOfWellKnownInsertion(path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.well-known/oauth-authorization-server/t", "/.well-known/oauth-authorization-server/t/"})
    @DisplayName("slugOfWellKnownInsertion: 마커 뒤가 비면 빈 문자열")
    void slugOfWellKnownInsertionWithoutSlug(String path) {
        assertThat(TenantPathMarker.slugOfWellKnownInsertion(path)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            // 삽입형 아님 — 문서 이름 바로 뒤가 마커가 아니다
            "/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/acme",
            "/.well-known/oauth-protected-resource/mcp", "/.well-known/t/acme", "/.well-known",
            // 첫 세그먼트가 .well-known 이 아니다(접두형은 slugOf 의 몫)
            "/t/acme/.well-known/oauth-authorization-server", "/api/v1/x", "/well-known/x/t/acme"})
    @DisplayName("slugOfWellKnownInsertion: 삽입형이 아니면 null")
    void slugOfWellKnownInsertionNotInsertion(String path) {
        assertThat(TenantPathMarker.slugOfWellKnownInsertion(path)).isNull();
    }
}
