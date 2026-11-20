package com.thinkfree.drive.httpclient.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IP 리터럴만 써서 DNS 없이 판정 규칙을 검사한다. */
class OutboundAddressGuardTest {

    private static OutboundAddressGuard guard(boolean enabled, String... allowed) {
        return new OutboundAddressGuard(new OutboundGuardProperties(enabled, List.of(allowed)));
    }

    @Nested
    @DisplayName("가드 꺼짐(기본) — 링크로컬만 차단")
    class Disabled {
        @ParameterizedTest
        @ValueSource(strings = {"8.8.8.8", "10.0.0.1", "192.168.1.1", "127.0.0.1", "::1"})
        void allowsEverythingButLinkLocal(String ip) {
            assertThatCode(() -> guard(false).resolveAndVerify(ip)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"169.254.169.254", "169.254.0.1", "fe80::1"})
        void blocksLinkLocal(String ip) {
            assertThatThrownBy(() -> guard(false).resolveAndVerify(ip)).isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
        }
    }

    @Nested
    @DisplayName("가드 켜짐 — 내부 대역 차단, 공인 IP 통과")
    class Enabled {
        @ParameterizedTest
        @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2001:4860:4860::8888"})
        void allowsPublic(String ip) {
            assertThatCode(() -> guard(true).resolveAndVerify(ip)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"127.0.0.1", "0.0.0.0", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1",
                "100.64.0.1", "224.0.0.1", "255.255.255.255", "169.254.169.254",
                "::1", "::", "fc00::1", "fd12:3456::1", "fe80::1",
                "2130706433", "::ffff:127.0.0.1", "::ffff:10.0.0.1"})
        void blocksInternal(String ip) {
            assertThatThrownBy(() -> guard(true).resolveAndVerify(ip)).isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
        }
    }

    @Nested
    @DisplayName("allowed-cidrs — 내부 대역 예외 허용, 링크로컬은 여전히 차단")
    class Allowed {
        @ParameterizedTest
        @ValueSource(strings = {"10.20.3.4", "10.20.255.1", "192.168.10.5"})
        void allowsListed(String ip) {
            assertThatCode(() -> guard(true, "10.20.0.0/16", "192.168.10.5/32").resolveAndVerify(ip)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"10.21.0.1", "192.168.10.6", "127.0.0.1"})
        void blocksUnlisted(String ip) {
            assertThatThrownBy(() -> guard(true, "10.20.0.0/16", "192.168.10.5/32").resolveAndVerify(ip))
                    .isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"169.254.169.254"})
        void linkLocalCannotBeAllowed(String ip) {
            assertThatThrownBy(() -> guard(true, "169.254.0.0/16").resolveAndVerify(ip))
                    .isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
        }
    }

    @Nested
    @DisplayName("resolveAllowed — 허용된 IP 만 남김(HTTP 연결용)")
    class ResolveAllowed {
        @org.junit.jupiter.api.Test
        @DisplayName("localhost 가 127.0.0.1·::1 로 조회돼도 127.0.0.0/8 만 허용하면 IPv4 만 남기고 통과")
        void keepsOnlyAllowedFamily() throws Exception {
            var addresses = guard(true, "127.0.0.0/8").resolveAllowed("localhost");
            org.assertj.core.api.Assertions.assertThat(addresses).isNotEmpty()
                    .allSatisfy(a -> org.assertj.core.api.Assertions.assertThat(a.getHostAddress()).startsWith("127."));
        }

        @org.junit.jupiter.api.Test
        @DisplayName("허용된 IP 가 하나도 없으면 차단")
        void blocksWhenNoneAllowed() {
            assertThatThrownBy(() -> guard(true).resolveAllowed("localhost")).isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
        }
    }
}
