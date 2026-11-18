package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DomainValidatorTest {

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @ParameterizedTest
        @ValueSource(strings = {
                "drive.com",
                "thinkfree.com",
                "aaaa.thinkfree.com",
                "aaaa.bbbbb.thinkfree.com",
                "www.drive.com",
                "add.drive.com",
                "add.thinkfree.com",
                "tenanta.company.co.kr",
                "a-b.c-d.com",
                "a1.b2.com",
                "sub1.sub2.sub3.example.org",
                "x.io",                                    // TLD 2자
                "very-long-subdomain-name-that-is-still-valid.example.com"
        })
        @DisplayName("정상 도메인은 true 를 반환한다")
        void returnsTrueForValidDomains(String domain) {
            assertThat(DomainValidator.isValid(domain)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "drive",                  // TLD 없음
                "drive.c",                // TLD 1자
                ".drive.com",             // 앞에 점
                "drive.com.",             // 뒤에 점
                "drive..com",             // 연속 점
                "-drive.com",             // 라벨이 하이픈으로 시작
                "drive-.com",             // 라벨이 하이픈으로 끝
                "drive.-com",             // TLD 가 하이픈으로 시작
                "Drive.com",              // 대문자
                "DRIVE.COM",              // 전부 대문자
                "drive_a.com",            // 언더스코어
                "drive!.com",             // 특수문자
                "drive .com",             // 공백
                "drive.com/path",         // 경로 포함
                "http://drive.com",       // 프로토콜 포함
                "drive.com:8080",         // 포트 포함
                "한글.com"                  // 비ASCII
        })
        @DisplayName("형식 잘못된 도메인은 false 를 반환한다")
        void returnsFalseForInvalidDomains(String domain) {
            assertThat(DomainValidator.isValid(domain)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("null/빈 값/공백은 false 를 반환한다")
        void returnsFalseForBlankOrNull(String domain) {
            assertThat(DomainValidator.isValid(domain)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "192.168.0.1",        // IPv4
                "127.0.0.1",          // localhost IP
                "10.0.0.1"
        })
        @DisplayName("IPv4 주소 형태는 거부된다 — TLD 가 영문 2자 이상이어야 함")
        void rejectsIpv4Pattern(String ip) {
            // 마지막 라벨이 숫자 1자라 [a-z]{2,63} TLD 패턴에 매칭 실패
            assertThat(DomainValidator.isValid(ip)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidIpv4 / isValidDomainOrIpv4 — PATH 모드 베이스 도메인(공개 호스트)용")
    class Ipv4 {

        @ParameterizedTest
        @ValueSource(strings = {"192.168.0.1", "127.0.0.1", "10.0.0.1", "0.0.0.0", "255.255.255.255", "172.16.254.9"})
        @DisplayName("옥텟 0~255 네 개는 IPv4 로 인정한다")
        void acceptsIpv4(String ip) {
            assertThat(DomainValidator.isValidIpv4(ip)).isTrue();
            assertThat(DomainValidator.isValidDomainOrIpv4(ip)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "256.1.1.1", "1.2.3", "1.2.3.4.5", "01.2.3.4", "1.2.3.04", "1.2.3.4:8080", "1.2.3.4/", " 1.2.3.4",
                "999.1.1.1", "a.b.c.d", "::1", "[::1]", "localhost"})
        @DisplayName("범위 밖·선행 0·옥텟 수 불일치·포트·IPv6·호스트명은 IPv4 가 아니다")
        void rejectsNonIpv4(String value) {
            assertThat(DomainValidator.isValidIpv4(value)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"drive.thinkfree.com", "add.thinkfree.com", "192.168.0.10"})
        @DisplayName("isValidDomainOrIpv4: 도메인 또는 IPv4 어느 쪽이든 통과")
        void domainOrIpv4(String host) {
            assertThat(DomainValidator.isValidDomainOrIpv4(host)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"localhost", "Drive.Thinkfree.com", "256.1.1.1", "drive", "-a.com"})
        @DisplayName("isValidDomainOrIpv4: 둘 다 아니면 거부(점 없는 호스트명·대문자·잘못된 IP)")
        void neitherDomainNorIpv4(String host) {
            assertThat(DomainValidator.isValidDomainOrIpv4(host)).isFalse();
        }

        @Test
        @DisplayName("isValid(도메인 전용)는 여전히 IPv4 를 거부한다 — SUBDOMAIN 모드 규칙 불변")
        void domainOnlyStillRejectsIpv4() {
            assertThat(DomainValidator.isValid("192.168.0.10")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"acme.192.168.0.10", "default-add.10.0.0.1", "a.b.127.0.0.1"})
        @DisplayName("isValidLabelsOnIpv4: 라벨(들) + IPv4 — PATH 모드 IPv4 베이스 위 테넌트 합성 domain")
        void acceptsLabelsOnIpv4(String host) {
            assertThat(DomainValidator.isValidLabelsOnIpv4(host)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"192.168.0.10", "acme.256.1.1.1", "Acme.192.168.0.10", "-acme.192.168.0.10", "acme..192.168.0.10",
                "acme.192.168.0", "acme.drive.thinkfree.com"})
        @DisplayName("isValidLabelsOnIpv4: 순수 IPv4·잘못된 옥텟·라벨 규칙 위반·도메인 TLD 는 아니다")
        void rejectsNonLabelsOnIpv4(String host) {
            assertThat(DomainValidator.isValidLabelsOnIpv4(host)).isFalse();
        }
    }

    @Test
    @DisplayName("isLocalhost — 단일 라벨 localhost 만(소문자). acme.localhost 는 isValid 가 이미 통과")
    void localhost() {
        assertThat(DomainValidator.isLocalhost("localhost")).isTrue();
        assertThat(DomainValidator.isLocalhost("LOCALHOST")).isFalse();
        assertThat(DomainValidator.isLocalhost("intranet")).isFalse();
        assertThat(DomainValidator.isLocalhost(null)).isFalse();
        assertThat(DomainValidator.isValid("acme.localhost")).isTrue();
    }

    @Nested
    @DisplayName("validateOrThrow")
    class ValidateOrThrow {

        @Test
        @DisplayName("정상 도메인은 예외를 던지지 않는다")
        void doesNotThrowForValid() {
            assertDoesNotThrow(() -> DomainValidator.validateOrThrow("tenanta.thinkfree.com", TenantErrorCode.INVALID_TENANT_DOMAIN));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "drive",
                "Drive.com",
                "drive_a.com",
                "drive..com",
                "-drive.com"
        })
        @DisplayName("형식 잘못된 도메인은 INVALID_TENANT_DOMAIN 예외를 던진다")
        void throwsForInvalid(String domain) {
            assertThatThrownBy(() -> DomainValidator.validateOrThrow(domain, TenantErrorCode.INVALID_TENANT_DOMAIN))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", TenantErrorCode.INVALID_TENANT_DOMAIN);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("null/빈/공백은 INVALID_TENANT_DOMAIN 예외를 던진다")
        void throwsForBlank(String domain) {
            assertThatThrownBy(() -> DomainValidator.validateOrThrow(domain, TenantErrorCode.INVALID_TENANT_DOMAIN))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", TenantErrorCode.INVALID_TENANT_DOMAIN);
        }
    }
}
