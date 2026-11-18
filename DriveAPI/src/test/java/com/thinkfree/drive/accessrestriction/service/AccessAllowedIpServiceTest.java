
package com.thinkfree.drive.accessrestriction.service;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedIpRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
@DisplayName("AccessAllowedIpService 통합테스트")
class AccessAllowedIpServiceTest {
    private static final int TENANT = 1;
    private static final RoleType ROLE = RoleType.ADMIN;
    @Autowired
    private AccessAllowedIpService accessAllowedIpService;
    @Autowired
    private AccessAllowedIpRepository accessAllowedIpRepository;
    @AfterEach
    void tearDown() {
        accessAllowedIpRepository.deleteByTenantAndRole(TENANT, ROLE);
    }
    @Nested
    @DisplayName("replaceAllowedIps() - CIDR 파싱 및 저장")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ReplaceAllowedIps {
        Stream<Arguments> validCidrs() {
            return Stream.of(
                    Arguments.of("203.0.113.1", "prefix 없는 단일 IP"),
                    Arguments.of("203.0.113.0/24", "일반 CIDR"),
                    Arguments.of("10.0.0.0/8", "넓은 대역"),
                    Arguments.of("192.168.1.1/32", "단일 IP CIDR"),
                    Arguments.of("0.0.0.0/0", "전체 허용")
            );
        }
        Stream<Arguments> invalidCidrs() {
            return Stream.of(
                    Arguments.of("not-an-ip", "유효하지 않은 문자열"),
                    Arguments.of("203.0.113.0/33", "prefix 범위 초과"),
                    Arguments.of("203.0.113.0/-1", "음수 prefix"),
                    Arguments.of("203.0.113.0/abc", "숫자가 아닌 prefix"),
                    Arguments.of("::1", "IPv6"),
                    Arguments.of("::1/128", "IPv6 CIDR")
            );
        }
        @ParameterizedTest(name = "[{index}] {0} ({1}) → 정상 저장")
        @MethodSource("validCidrs")
        @DisplayName("유효한 IP/CIDR → 정상 저장")
        void replaceAllowedIps_validCidr_savedSuccessfully(String cidr, String description) {
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of(cidr));
            List<String> result = accessAllowedIpService.findAllIpCidrs(TENANT, ROLE);
            assertThat(result).hasSize(1);
        }
        @Test
        @DisplayName("prefix 없는 IP → /32로 저장")
        void replaceAllowedIps_ipWithoutPrefix_savedAs32() {
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of("203.0.113.5"));
            List<String> result = accessAllowedIpService.findAllIpCidrs(TENANT, ROLE);
            assertThat(result).containsExactly("203.0.113.5/32");
        }
        @Test
        @DisplayName("여러 IP/CIDR → 모두 저장")
        void replaceAllowedIps_multipleCidrs_allSaved() {
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of("203.0.113.0/24", "10.0.0.0/8", "192.168.1.1/32"));
            List<String> result = accessAllowedIpService.findAllIpCidrs(TENANT, ROLE);
            assertThat(result).hasSize(3).containsExactlyInAnyOrder("203.0.113.0/24", "10.0.0.0/8", "192.168.1.1/32");
        }
        @Test
        @DisplayName("기존 목록을 새 목록으로 교체")
        void replaceAllowedIps_replacesExisting() {
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of("203.0.113.0/24", "10.0.0.0/8"));
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of("192.168.1.1/32"));
            List<String> result = accessAllowedIpService.findAllIpCidrs(TENANT, ROLE);
            assertThat(result).containsExactly("192.168.1.1/32");
        }
        @Test
        @DisplayName("빈 목록 → 전체 삭제")
        void replaceAllowedIps_emptyList_deletesAll() {
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of("203.0.113.0/24"));
            accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of());
            List<String> result = accessAllowedIpService.findAllIpCidrs(TENANT, ROLE);
            assertThat(result).isEmpty();
        }
        @ParameterizedTest(name = "[{index}] {0} ({1}) → INVALID_IP_CIDR 예외")
        @MethodSource("invalidCidrs")
        @DisplayName("유효하지 않은 IP/CIDR → INVALID_IP_CIDR 예외")
        void replaceAllowedIps_invalidCidr_throwsException(String cidr, String description) {
            assertThatThrownBy(() ->
                    accessAllowedIpService.replaceAllowedIps(TENANT, ROLE, 1, List.of(cidr)))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> {
                        ApiRuntimeException apiEx = (ApiRuntimeException) ex;
                        assertThat(apiEx.getCode()).isEqualTo(AccessErrorCode.INVALID_IP_CIDR);
                    });
        }
    }
}
