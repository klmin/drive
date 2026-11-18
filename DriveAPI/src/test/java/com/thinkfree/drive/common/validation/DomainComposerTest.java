package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DomainComposer — 서브도메인 형식 정규화")
class DomainComposerTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "default", "a-b", "a1.b2", "tenant-01",
            // 루트 라우트와 같은 이름도 허용 — /t/{slug} 마커 문법이라 예약어가 없다
            "drive", "api", "mcp", "oauth2", "t"})
    @DisplayName("정상 서브도메인은 그대로 반환 (예약어 없음)")
    void acceptsValid(String subdomain) {
        assertThat(DomainComposer.isValidSubdomain(subdomain)).isTrue();
        assertThat(DomainComposer.normalizeSubdomain(subdomain)).isEqualTo(subdomain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme", "-acme", "acme-", "ac me", "ac_me", ""})
    @DisplayName("형식 오류는 INVALID_TENANT_DOMAIN")
    void rejectsInvalidFormat(String subdomain) {
        assertThat(DomainComposer.isValidSubdomain(subdomain)).isFalse();
        assertThatThrownBy(() -> DomainComposer.normalizeSubdomain(subdomain))
                .isInstanceOf(ApiRuntimeException.class)
                .hasFieldOrPropertyWithValue("code", TenantErrorCode.INVALID_TENANT_DOMAIN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("isValidSubdomain — null·빈 문자열·공백은 false")
    void blankIsInvalid(String subdomain) {
        assertThat(DomainComposer.isValidSubdomain(subdomain)).isFalse();
    }

    @Test
    @DisplayName("compose — subdomain 이 비면 베이스 자체")
    void compose() {
        assertThat(DomainComposer.compose("acme", "drive.thinkfree.com")).isEqualTo("acme.drive.thinkfree.com");
        assertThat(DomainComposer.compose("", "drive.thinkfree.com")).isEqualTo("drive.thinkfree.com");
        assertThat(DomainComposer.compose(null, "drive.thinkfree.com")).isEqualTo("drive.thinkfree.com");
    }

    @Test
    @DisplayName("extractLeadingSubdomain — 첫 라벨, 점 없는 호스트(localhost)·IPv4 는 null(루트 테넌트)")
    void extractLeadingSubdomain() {
        assertThat(DomainComposer.extractLeadingSubdomain("drive.example.com")).isEqualTo("drive");
        assertThat(DomainComposer.extractLeadingSubdomain("acme.localhost")).isEqualTo("acme");
        assertThat(DomainComposer.extractLeadingSubdomain("localhost")).isNull();
        assertThat(DomainComposer.extractLeadingSubdomain("192.168.0.10")).as("첫 옥텟 192 는 서브도메인이 아님").isNull();
        assertThat(DomainComposer.extractLeadingSubdomain(null)).isNull();
    }
}
