
package com.thinkfree.drive.access.service;
import com.thinkfree.drive.access.dto.IpAccessCheckCommand;
import com.thinkfree.drive.accessrestriction.cache.AccessPolicyCache;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedCountryRepository;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedIpRepository;
import com.thinkfree.drive.fixture.IpFixtures;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.thinkfree.drive.common.DriveIntegrationTest;
import java.util.List;
import java.util.stream.Stream;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@DriveIntegrationTest
@DisplayName("IpAccessControlService 통합테스트")
@Slf4j
//@Disabled
class IpAccessControlServiceTest {
    private static final int TENANT = 1;
    private static final RoleType ROLE = RoleType.USER;
    @Autowired
    private IpAccessControlService ipAccessControlService;
    @Autowired
    private AccessAllowedCountryRepository accessAllowedCountryRepository;
    @Autowired
    private AccessAllowedIpRepository accessAllowedIpRepository;
    @Autowired
    private SystemConfigRepository systemConfigRepository;
    @Autowired
    private AccessPolicyCache accessPolicyCache;
    @AfterEach
    void tearDown() {
        accessAllowedCountryRepository.deleteByTenant(TENANT);
        accessAllowedIpRepository.deleteByTenantAndRole(TENANT, ROLE);
        accessPolicyCache.invalidate(TENANT); // 같은 컨텍스트를 쓰는 다른 테스트가 이 테스트의 정책으로 판정되지 않게
    }
    private void setAccessRestrictionEnabled(boolean enabled) {
        systemConfigRepository.upsert(SystemConfig.createSystemConfig(
                TENANT,
                SystemConfigKey.ACCESS_RESTRICTION_STATUS.getDbKey(),
                String.valueOf(enabled),
                1
        ));
    }
    /** 이 테스트는 정책을 리포지토리로 바로 써서 서비스의 캐시 무효화를 거치지 않는다 — 판정 전에 비워 DB 값으로 판정하게 한다 */
    private void check(IpAccessCheckCommand command) {
        accessPolicyCache.invalidate(TENANT);
        ipAccessControlService.checkAllowed(command);
    }
    private static IpAccessCheckCommand cmd(String ip) {
        return new IpAccessCheckCommand(ip, ROLE, TENANT);
    }
    private static IpAccessCheckCommand cmd(String ip, RoleType role) {
        return new IpAccessCheckCommand(ip, role, TENANT);
    }
    @Nested
    @DisplayName("접근 제한 비활성화(enabled=false) + 국가 목록 비어있음")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenAccessRestrictionDisabled {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(false);
            accessAllowedCountryRepository.deleteByTenant(TENANT);
        }
        Stream<Arguments> anyIps() {
            return Stream.of(
                    Arguments.of("KR", IpFixtures.IP_KR),
                    Arguments.of("US", IpFixtures.IP_US),
                    Arguments.of("JP", IpFixtures.IP_JP),
                    Arguments.of("사설 IP", IpFixtures.IP_PRIVATE)
            );
        }
        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → 허용")
        @MethodSource("anyIps")
        @DisplayName("접근 제한이 비활성화되어 있으면 모든 IP → 허용")
        void isAllowed_disabledRestriction_returnsTrue(String countryCode, String ip) {
            log.info("[비활성화] countryCode : {}, ip : {}", countryCode, ip);
            assertThatNoException().isThrownBy(() -> check(cmd(ip)));
        }
    }
    @Nested
    @DisplayName("접근 제한 활성화(enabled=true) + 허용 국가 목록이 비어있는 경우")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenAccessRestrictionEnabledEmptyList {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(true);
            accessAllowedCountryRepository.deleteByTenant(TENANT);
        }
        Stream<Arguments> anyIps() {
            return Stream.of(
                    Arguments.of("KR", IpFixtures.IP_KR),
                    Arguments.of("US", IpFixtures.IP_US),
                    Arguments.of("JP", IpFixtures.IP_JP),
                    Arguments.of("CN", IpFixtures.IP_CN)
            );
        }
        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → 허용")
        @MethodSource("anyIps")
        @DisplayName("활성화 상태에서 허용 국가 목록이 비어있으면 국가 제한 없음 → 모든 IP 허용")
        void isAllowed_enabledWithEmptyCountryList_passes(String countryCode, String ip) {
            log.info("[국가 제한 없음] countryCode : {}, ip : {}", countryCode, ip);
            assertThatNoException().isThrownBy(() -> check(cmd(ip)));
        }
        @Test
        @DisplayName("사설 IP → 국가 제한 없음 → 허용")
        void isAllowed_privateIp_passes() {
            assertThatNoException().isThrownBy(() -> check(cmd(IpFixtures.IP_PRIVATE)));
        }
    }
    @Nested
    @DisplayName("접근 제한 활성화(enabled=true) + KR만 허용 국가로 등록된 경우")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenAccessRestrictionEnabledOnlyKr {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(true);
            accessAllowedCountryRepository.deleteByTenant(TENANT);
            accessAllowedCountryRepository.insertAll(List.of(
                    AccessAllowedCountry.create(TENANT, "KR", 1)
            ));
        }
        Stream<Arguments> allowedIps() {
            return Stream.of(
                    Arguments.of("KR", IpFixtures.IP_KR),
                    Arguments.of("사설 IP", IpFixtures.IP_PRIVATE)
            );
        }
        Stream<Arguments> blockedIps() {
            return Stream.of(
                    Arguments.of("US", IpFixtures.IP_US),
                    Arguments.of("JP", IpFixtures.IP_JP),
                    Arguments.of("CN", IpFixtures.IP_CN),
                    Arguments.of("DE", IpFixtures.IP_DE),
                    Arguments.of("RU", IpFixtures.IP_RU)
            );
        }
        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → 허용")
        @MethodSource("allowedIps")
        @DisplayName("허용 국가(KR) IP → 실제 GeoIP DB 조회 → 허용")
        void isAllowed_allowedIp_returnsTrue(String countryCode, String ip) {
            log.info("[success] countryCode : {}, ip : {}", countryCode, ip);
            assertThatNoException().isThrownBy(() -> check(cmd(ip)));
        }
        @ParameterizedTest(name = "[{index}] 국가코드 {0} ({1}) → 차단")
        @MethodSource("blockedIps")
        @DisplayName("허용 국가 외 IP → 실제 GeoIP DB 조회 → BLOCKED_COUNTRY")
        void isAllowed_nonAllowedIp_returnsFalse(String countryCode, String ip) {
            log.info("[fail] countryCode : {}, ip : {}", countryCode, ip);
            assertThatThrownBy(() -> check(cmd(ip)))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(AccessErrorCode.BLOCKED_COUNTRY));
        }
    }
    @Nested
    @DisplayName("접근 제한 활성화(enabled=true) + KR 허용 국가 + 허용 IP 목록 있는 경우")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenIpListSetWithKrCountry {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(true);
            accessAllowedCountryRepository.deleteByTenant(TENANT);
            accessAllowedCountryRepository.insertAll(List.of(
                    AccessAllowedCountry.create(TENANT, "KR", 1)
            ));
            accessAllowedIpRepository.deleteByTenantAndRole(TENANT, ROLE);
            accessAllowedIpRepository.insertAll(List.of(
                    AccessAllowedIp.create(TENANT, ROLE, "168.126.63.1", 32, 1) // IP_KR 정확히 매칭
            ));
        }
        @Test
        @DisplayName("허용 국가(KR) + 허용 IP 목록에 포함된 IP → 허용")
        void isAllowed_allowedCountryAndAllowedIp_returnsTrue() {
            assertThatNoException().isThrownBy(() -> check(cmd(IpFixtures.IP_KR)));
        }
        @Test
        @DisplayName("허용 국가(KR) + 허용 IP 목록에 없는 IP → 국가로 통과 (OR 조건)")
        void isAllowed_allowedCountryButNotAllowedIp_passedByCountry() {
            // KR 국가이지만 허용 IP 목록에 없는 다른 KR IP → 국가 일치로 통과
            assertThatNoException().isThrownBy(() -> check(cmd("168.126.63.2")));
        }
        @Test
        @DisplayName("차단 국가(JP) + 허용 IP 목록에 있는 IP → IP로 통과 (OR 조건)")
        void isAllowed_blockedCountryButAllowedIp_passedByIp() {
            // JP는 차단 국가이지만 IP 목록에 등록 → IP 일치로 통과
            accessAllowedIpRepository.insertAll(List.of(AccessAllowedIp.create(TENANT, ROLE, IpFixtures.IP_JP, 32, 1)));
            assertThatNoException().isThrownBy(() -> check(cmd(IpFixtures.IP_JP)));
        }
        @Test
        @DisplayName("차단 국가(JP) + 허용 IP 목록에도 없는 IP → BLOCKED_COUNTRY")
        void isAllowed_blockedCountryAndNotAllowedIp_throwsBlockedCountry() {
            assertThatThrownBy(() -> check(cmd(IpFixtures.IP_JP)))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(AccessErrorCode.BLOCKED_COUNTRY));
        }
    }
    @Nested
    @DisplayName("접근 제한 활성화(enabled=true) + 사설 IP + 허용 IP 목록 있는 경우")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenPrivateIpWithIpList {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(true);
            accessAllowedCountryRepository.deleteByTenant(TENANT);
            accessAllowedIpRepository.deleteByTenantAndRole(TENANT, ROLE);
            accessAllowedIpRepository.insertAll(List.of(
                    AccessAllowedIp.create(TENANT, ROLE, "192.168.0.0", 24, 1) // 192.168.0.0/24 대역
            ));
        }
        @Test
        @DisplayName("사설 IP + 허용 CIDR 범위 내 → 허용")
        void isAllowed_privateIpInCidr_returnsTrue() {
            // IP_PRIVATE = "192.168.0.1" → 192.168.0.0/24 범위 내
            assertThatNoException().isThrownBy(() -> check(cmd(IpFixtures.IP_PRIVATE)));
        }
        @Test
        @DisplayName("사설 IP + 허용 CIDR 범위 밖 → BLOCKED_IP")
        void isAllowed_privateIpNotInCidr_returnsFalse() {
            // 192.168.1.1 → 192.168.0.0/24 범위 밖
            assertThatThrownBy(() -> check(cmd("192.168.1.1")))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
    }

    @Nested
    @DisplayName("ADMIN role - IP만 체크 (country 미적용)")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenAdminRole {
        @BeforeEach
        void setUp() {
            setAccessRestrictionEnabled(true);
            // country가 등록되어 있어도 ADMIN 분기에서는 조회 자체를 안 함
            accessAllowedCountryRepository.deleteByTenant(TENANT);
            accessAllowedCountryRepository.insertAll(List.of(
                    AccessAllowedCountry.create(TENANT, "KR", 1)
            ));
            accessAllowedIpRepository.deleteByTenantAndRole(TENANT, RoleType.ADMIN);
            accessAllowedIpRepository.insertAll(List.of(
                    AccessAllowedIp.create(TENANT, RoleType.ADMIN, "10.0.0.0", 24, 1)
            ));
        }
        @AfterEach
        void cleanupAdmin() {
            accessAllowedIpRepository.deleteByTenantAndRole(TENANT, RoleType.ADMIN);
        }
        @Test
        @DisplayName("ADMIN + 허용 IP 매칭 → 허용 (KR 국가 체크 무시)")
        void admin_allowedIp_passes() {
            assertThatNoException().isThrownBy(() -> check(cmd("10.0.0.5", RoleType.ADMIN)));
        }
        @Test
        @DisplayName("ADMIN + 허용 IP 미매칭 → BLOCKED_IP (KR 국가 코드여도 차단)")
        void admin_notAllowedIp_throwsBlockedIp() {
            assertThatThrownBy(() -> check(cmd(IpFixtures.IP_KR, RoleType.ADMIN)))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(AccessErrorCode.BLOCKED_IP));
        }
        @Test
        @DisplayName("ADMIN + IP 목록 비어있음 → 통과")
        void admin_emptyIpList_passes() {
            accessAllowedIpRepository.deleteByTenantAndRole(TENANT, RoleType.ADMIN);
            assertThatNoException().isThrownBy(() -> check(cmd(IpFixtures.IP_US, RoleType.ADMIN)));
        }
    }

}
