package com.thinkfree.drive.accessrestriction.service;

import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedCountryRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("AccessAllowedCountryService 통합테스트")
class AccessAllowedCountryServiceTest {

    private static final int TENANT = 1;

    @Autowired
    private AccessAllowedCountryService accessAllowedCountryService;

    @Autowired
    private AccessAllowedCountryRepository accessAllowedCountryRepository;

    @AfterEach
    void tearDown() {
        accessAllowedCountryRepository.deleteByTenant(TENANT);
    }

    @Nested
    @DisplayName("replaceAllowedCountries() - 국가 코드 검증 및 저장")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ReplaceAllowedCountries {

        Stream<Arguments> validCodes() {
            return Stream.of(
                    Arguments.of("KR", "대한민국"),
                    Arguments.of("US", "미국"),
                    Arguments.of("JP", "일본"),
                    Arguments.of("DE", "독일"),
                    Arguments.of("BR", "브라질")
            );
        }

        Stream<Arguments> invalidCodes() {
            return Stream.of(
                    Arguments.of("ZZ", "존재하지 않는 코드"),
                    Arguments.of("KOR", "ISO alpha-3 코드"),
                    Arguments.of("kr", "소문자"),
                    Arguments.of("123", "숫자"),
                    Arguments.of("", "빈 문자열")
            );
        }

        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → 정상 저장")
        @MethodSource("validCodes")
        @DisplayName("유효한 국가 코드 → 정상 저장")
        void replaceAllowedCountries_validCode_savedSuccessfully(String code, String description) {
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of(code));

            List<String> result = accessAllowedCountryService.findAllCountryCodes(TENANT);
            assertThat(result).containsExactly(code);
        }

        @Test
        @DisplayName("여러 국가 코드 → 모두 저장")
        void replaceAllowedCountries_multipleCodes_allSaved() {
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of("KR", "US", "JP"));

            List<String> result = accessAllowedCountryService.findAllCountryCodes(TENANT);
            assertThat(result).hasSize(3).containsExactlyInAnyOrder("KR", "US", "JP");
        }

        @Test
        @DisplayName("기존 목록을 새 목록으로 교체")
        void replaceAllowedCountries_replacesExisting() {
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of("KR", "US"));
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of("JP"));

            List<String> result = accessAllowedCountryService.findAllCountryCodes(TENANT);
            assertThat(result).containsExactly("JP");
        }

        @Test
        @DisplayName("빈 목록 → 전체 삭제")
        void replaceAllowedCountries_emptyList_deletesAll() {
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of("KR"));
            accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of());

            List<String> result = accessAllowedCountryService.findAllCountryCodes(TENANT);
            assertThat(result).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → INVALID_COUNTRY_CODE 예외")
        @MethodSource("invalidCodes")
        @DisplayName("유효하지 않은 국가 코드 → INVALID_COUNTRY_CODE 예외")
        void replaceAllowedCountries_invalidCode_throwsException(String code, String description) {
            assertThatThrownBy(() ->
                    accessAllowedCountryService.replaceAllowedCountries(TENANT, 1, List.of(code)))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> {
                        ApiRuntimeException apiEx = (ApiRuntimeException) ex;
                        assertThat(apiEx.getCode()).isEqualTo(AccessErrorCode.INVALID_COUNTRY_CODE);
                    });
        }
    }
}
