package com.thinkfree.drive.superadmin.security;

import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.properties.SuperAdminIpProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SuperAdminIpGuard 단위 테스트")
class SuperAdminIpGuardTest {

    private static SuperAdminIpGuard guard(String... allowedIps) {
        return new SuperAdminIpGuard(new SuperAdminIpProperty(List.of(allowedIps)));
    }

    @Nested
    @DisplayName("단일 IP 허용 (/32)")
    class SingleIp {

        private final SuperAdminIpGuard guard = guard("10.0.0.1");

        @Test
        @DisplayName("정확히 일치하는 IP → 통과")
        void exactMatch_passes() {
            assertThatNoException().isThrownBy(() -> guard.checkAllowed("10.0.0.1"));
        }

        @Test
        @DisplayName("다른 IP → BLOCKED_IP")
        void differentIp_throwsBlockedIp() {
            assertThatThrownBy(() -> guard.checkAllowed("10.0.0.2"))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
    }

    @Nested
    @DisplayName("CIDR 대역 허용")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CidrRange {

        private final SuperAdminIpGuard guard = guard("192.168.0.0/24");

        Stream<Arguments> withinRange() {
            return Stream.of(
                    Arguments.of("192.168.0.0", "네트워크 주소"),
                    Arguments.of("192.168.0.1", "범위 첫 호스트"),
                    Arguments.of("192.168.0.100", "범위 중간"),
                    Arguments.of("192.168.0.255", "범위 마지막")
            );
        }

        Stream<Arguments> outOfRange() {
            return Stream.of(
                    Arguments.of("192.168.1.0", "다음 서브넷"),
                    Arguments.of("192.168.1.1", "다른 대역"),
                    Arguments.of("10.0.0.1", "완전 다른 IP"),
                    Arguments.of("172.16.0.1", "다른 사설")
            );
        }

        @ParameterizedTest(name = "[{index}] {0} ({1}) → 통과")
        @MethodSource("withinRange")
        @DisplayName("CIDR 대역 안의 IP → 통과")
        void withinCidr_passes(String ip, String description) {
            assertThatNoException().isThrownBy(() -> guard.checkAllowed(ip));
        }

        @ParameterizedTest(name = "[{index}] {0} ({1}) → BLOCKED_IP")
        @MethodSource("outOfRange")
        @DisplayName("CIDR 대역 밖의 IP → BLOCKED_IP")
        void outsideCidr_throwsBlockedIp(String ip, String description) {
            assertThatThrownBy(() -> guard.checkAllowed(ip))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
    }

    @Nested
    @DisplayName("여러 항목 혼합 (단일 IP + CIDR)")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class MixedAllowList {

        private final SuperAdminIpGuard guard = guard("10.0.0.1", "192.168.0.0/24", "172.16.5.5");

        Stream<Arguments> allowed() {
            return Stream.of(
                    Arguments.of("10.0.0.1", "단일 IP 정확 일치"),
                    Arguments.of("192.168.0.50", "CIDR 대역 내"),
                    Arguments.of("172.16.5.5", "두 번째 단일 IP")
            );
        }

        @ParameterizedTest(name = "[{index}] {0} ({1}) → 통과")
        @MethodSource("allowed")
        @DisplayName("허용 목록 중 하나라도 일치 → 통과")
        void anyMatch_passes(String ip, String description) {
            assertThatNoException().isThrownBy(() -> guard.checkAllowed(ip));
        }

        @Test
        @DisplayName("어느 것도 일치 안 함 → BLOCKED_IP")
        void noneMatch_throwsBlockedIp() {
            assertThatThrownBy(() -> guard.checkAllowed("8.8.8.8"))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
    }

    @Nested
    @DisplayName("엣지 케이스 - 잘못된 입력")
    class EdgeCases {

        private final SuperAdminIpGuard guard = guard("10.0.0.1");

        @Test
        @DisplayName("null clientIp → BLOCKED_IP")
        void nullClientIp_throwsBlockedIp() {
            assertThatThrownBy(() -> guard.checkAllowed(null))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }

        @Test
        @DisplayName("빈 문자열 clientIp → BLOCKED_IP")
        void blankClientIp_throwsBlockedIp() {
            assertThatThrownBy(() -> guard.checkAllowed(""))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }

        @Test
        @DisplayName("잘못된 IP 포맷 → BLOCKED_IP (IpAddressMatcher가 IllegalArgumentException 던져도 false 처리)")
        void invalidIpFormat_throwsBlockedIp() {
            assertThatThrownBy(() -> guard.checkAllowed("not-an-ip"))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode())
                            .isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
    }
}
