package com.thinkfree.drive.accessrestriction.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CountryCodeRegistry 단위 테스트")
class CountryCodeRegistryTest {

    private CountryCodeRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new CountryCodeRegistry();
        registry.init();
    }

    @Nested
    @DisplayName("isValid() - 유효성 검증")
    class IsValid {

        @Test
        @DisplayName("CSV에 존재하는 국가 코드 → true")
        void validCodes_returnTrue() {
            assertThat(registry.isValid("KR")).isTrue();
            assertThat(registry.isValid("US")).isTrue();
            assertThat(registry.isValid("JP")).isTrue();
            assertThat(registry.isValid("DE")).isTrue();
        }

        @Test
        @DisplayName("null → false")
        void null_returnsFalse() {
            assertThat(registry.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열 → false")
        void emptyString_returnsFalse() {
            assertThat(registry.isValid("")).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 임의 코드 → false")
        void unknownCode_returnsFalse() {
            assertThat(registry.isValid("ZZ")).isFalse();
            assertThat(registry.isValid("XX")).isFalse();
            assertThat(registry.isValid("00")).isFalse();
        }

        @Test
        @DisplayName("소문자 코드는 인식하지 않음 → false")
        void lowercaseCode_returnsFalse() {
            assertThat(registry.isValid("kr")).isFalse();
            assertThat(registry.isValid("us")).isFalse();
        }

        @Test
        @DisplayName("기존 enum에 누락되어 있던 국가 코드도 유효해야 함")
        void codesAbsentInOldEnum_returnTrue() {
            // 기존 CountryIsoCode enum에서 누락됐던 국가들
            assertThat(registry.isValid("GE")).isTrue(); // Georgia (조지아)
            assertThat(registry.isValid("PS")).isTrue(); // Palestine (팔레스타인)
            assertThat(registry.isValid("SS")).isTrue(); // South Sudan (남수단)
        }
    }

    @Nested
    @DisplayName("getName() - 국가명 조회")
    class GetName {

        @Test
        @DisplayName("유효한 코드 → 국가명 반환")
        void validCode_returnsName() {
            assertThat(registry.getName("KR")).isEqualTo("South Korea");
            assertThat(registry.getName("US")).isEqualTo("United States");
            assertThat(registry.getName("JP")).isEqualTo("Japan");
        }

        @Test
        @DisplayName("CSV 따옴표로 감싼 국가명(쉼표 포함) → 따옴표 없이 전체 이름")
        void quotedName_returnsUnquotedName() {
            assertThat(registry.getName("AE")).isEqualTo("United Arab Emirates");
            assertThat(registry.getName("BQ")).isEqualTo("Bonaire, Sint Eustatius and Saba");
        }

        @Test
        @DisplayName("존재하지 않는 코드 → null 반환")
        void invalidCode_returnsNull() {
            assertThat(registry.getName("ZZ")).isNull();
        }

        @Test
        @DisplayName("null → null 반환")
        void null_returnsNull() {
            assertThat(registry.getName(null)).isNull();
        }
    }

    @Nested
    @DisplayName("getAll() - 전체 목록 조회")
    class GetAll {

        @Test
        @DisplayName("국가 목록이 비어있지 않음")
        void getAll_isNotEmpty() {
            assertThat(registry.getAll()).isNotEmpty();
        }

        @Test
        @DisplayName("반환된 Map은 수정 불가 (unmodifiable)")
        void getAll_isUnmodifiable() {
            var all = registry.getAll();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> all.put("ZZ", "Fake Country")
            );
        }

        @Test
        @DisplayName("코드와 국가명이 올바르게 매핑되어 있음")
        void getAll_containsCorrectMappings() {
            var all = registry.getAll();
            assertThat(all).containsEntry("KR", "South Korea")
                           .containsEntry("US", "United States")
                           .containsKey("GE");
        }
    }
}
