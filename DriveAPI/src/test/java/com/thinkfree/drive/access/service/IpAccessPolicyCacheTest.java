package com.thinkfree.drive.access.service;

import com.thinkfree.drive.access.dto.IpAccessCheckCommand;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.cache.AccessPolicyCache;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.systemconfig.data.AccessRestrictionData;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.userlog.service.IpLocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * IP 접근 정책 캐시 — (테넌트, 역할) 단위로 보관, 테넌트별로 비운다. DB 없이 조회 서비스를 목으로 둔다.
 */
@DisplayName("IpAccessControlService + AccessPolicyCache")
class IpAccessPolicyCacheTest {

    static final int T1 = 1;
    static final int T2 = 2;
    static final String IP = "10.0.0.5";

    AccessAllowedIpService ipService = mock(AccessAllowedIpService.class);
    AccessAllowedCountryService countryService = mock(AccessAllowedCountryService.class);
    SystemConfigFindService configService = mock(SystemConfigFindService.class);
    AccessPolicyCache cache = new AccessPolicyCache();
    IpAccessControlService service = new IpAccessControlService(mock(IpLocationService.class), countryService, ipService, configService, cache);

    @BeforeEach
    void setUp() {
        enabled(T1, true);
        enabled(T2, true);
        given(countryService.findAllCountryCodes(T1)).willReturn(List.of());
        given(countryService.findAllCountryCodes(T2)).willReturn(List.of());
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void enabled(int tenant, boolean value) {
        given(configService.findAccessRestrictionStatusConfig(tenant)).willReturn(AccessRestrictionData.builder().enabled(value).build());
    }

    private void check(int tenant, RoleType role) {
        service.checkAllowed(new IpAccessCheckCommand(IP, role, tenant));
    }

    @Test
    @DisplayName("TTL 안에서는 같은 테넌트 정책을 DB 에서 다시 읽지 않는다")
    void cachedWithinTtl() {
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));

        for (int i = 0; i < 5; i++) {
            check(T1, RoleType.USER);
        }
        verify(ipService, times(1)).findAllIpCidrs(T1, RoleType.USER);
        verify(configService, times(1)).findAccessRestrictionStatusConfig(T1);
    }

    @Test
    @DisplayName("테넌트마다 따로 — 한 테넌트의 정책으로 다른 테넌트를 판정하지 않는다")
    void isolatedPerTenant() {
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));
        given(ipService.findAllIpCidrs(T2, RoleType.USER)).willReturn(List.of("192.168.0.0/16"));

        assertThatNoException().isThrownBy(() -> check(T1, RoleType.USER));
        assertThatThrownBy(() -> check(T2, RoleType.USER))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(AccessErrorCode.BLOCKED_IP);
    }

    @Test
    @DisplayName("ADMIN 정책과 USER 정책은 따로, ANONYMOUS 는 USER 정책을 같이 쓴다")
    void rolesSeparated() {
        given(ipService.findAllIpCidrs(T1, RoleType.ADMIN)).willReturn(List.of("172.16.0.0/12"));
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));

        assertThatNoException().isThrownBy(() -> check(T1, RoleType.USER));
        assertThatNoException().isThrownBy(() -> check(T1, RoleType.ANONYMOUS));
        assertThatThrownBy(() -> check(T1, RoleType.ADMIN)).isInstanceOf(ApiRuntimeException.class);
        verify(ipService, times(1)).findAllIpCidrs(T1, RoleType.USER);
        verify(ipService, times(1)).findAllIpCidrs(T1, RoleType.ADMIN);
    }

    @Test
    @DisplayName("invalidate(테넌트) — 그 테넌트의 ADMIN·USER 정책만 다시 읽는다")
    void invalidateOnlyThatTenant() {
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));
        given(ipService.findAllIpCidrs(T1, RoleType.ADMIN)).willReturn(List.of("10.0.0.0/24"));
        given(ipService.findAllIpCidrs(T2, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));
        check(T1, RoleType.USER);
        check(T1, RoleType.ADMIN);
        check(T2, RoleType.USER);

        // 사용자 허용 IP 를 바꿨다 — 바로 다음 요청부터 막힌다
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("192.168.0.0/16"));
        cache.invalidate(T1);

        assertThatThrownBy(() -> check(T1, RoleType.USER)).isInstanceOf(ApiRuntimeException.class);
        check(T1, RoleType.ADMIN);
        check(T2, RoleType.USER);
        verify(ipService, times(2)).findAllIpCidrs(T1, RoleType.USER);
        verify(ipService, times(2)).findAllIpCidrs(T1, RoleType.ADMIN);
        verify(ipService, times(1)).findAllIpCidrs(T2, RoleType.USER);
    }

    @Test
    @DisplayName("사용 여부를 끄면 다음 요청부터 검사하지 않는다")
    void disableTakesEffectAfterInvalidate() {
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("192.168.0.0/16"));
        assertThatThrownBy(() -> check(T1, RoleType.USER)).isInstanceOf(ApiRuntimeException.class);

        enabled(T1, false);
        cache.invalidate(T1);
        assertThatNoException().isThrownBy(() -> check(T1, RoleType.USER));
    }

    @Test
    @DisplayName("트랜잭션 안의 invalidate — 커밋 뒤 한 번 더 비워, 커밋 전에 다시 담긴 이전 정책이 남지 않는다")
    void invalidateAgainAfterCommit() {
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("10.0.0.0/24"));
        TransactionSynchronizationManager.initSynchronization();

        cache.invalidate(T1);
        check(T1, RoleType.USER); // 커밋 전 — 이전 정책을 다시 읽어 담는다
        given(ipService.findAllIpCidrs(T1, RoleType.USER)).willReturn(List.of("192.168.0.0/16")); // 커밋된 새 정책

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        assertThatThrownBy(() -> check(T1, RoleType.USER)).isInstanceOf(ApiRuntimeException.class);
    }
}
