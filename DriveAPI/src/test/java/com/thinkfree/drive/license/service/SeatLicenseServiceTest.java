package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.SeatLicenseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * SeatLicenseService 단위 테스트.
 * isSeatAvailable 가 LicenseService.getSeatPolicy 를 통해 SEAT 여부와 constrain 을 함께 가져와
 * 비-SEAT 라이선스는 자동 통과시키도록 게이트 책임을 흡수했다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeatLicenseService 단위 테스트")
class SeatLicenseServiceTest {

    private static final int TENANT_A = 1;
    private static final int TENANT_B = 2;

    @Mock
    private SeatLicenseRepository seatLicenseRepository;

    @Mock
    private LicenseService licenseService;

    @InjectMocks
    private SeatLicenseService seatLicenseService;

    private LicenseService.SeatPolicy seat(int constrainValue) {
        return new LicenseService.SeatPolicy(true, constrainValue);
    }

    private LicenseService.SeatPolicy nonSeat() {
        return new LicenseService.SeatPolicy(false, 0);
    }

    @Nested
    @DisplayName("getCurrentActiveUserNum(tenantSeq)")
    class GetCurrentActiveUserNum {

        @Test
        @DisplayName("mapper.countActiveUsers(tenantSeq) 로 위임")
        void delegatesToRepository() {
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(42);

            int result = seatLicenseService.getCurrentActiveUserNum(TENANT_A);

            assertThat(result).isEqualTo(42);
            then(seatLicenseRepository).should().countActiveUsers(TENANT_A);
        }

        @Test
        @DisplayName("테넌트 격리 - 테넌트별로 mapper 가 따로 호출됨")
        void perTenantIsolation() {
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(10);
            given(seatLicenseRepository.countActiveUsers(TENANT_B)).willReturn(50);

            assertThat(seatLicenseService.getCurrentActiveUserNum(TENANT_A)).isEqualTo(10);
            assertThat(seatLicenseService.getCurrentActiveUserNum(TENANT_B)).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("getConstrainValue(tenantSeq)")
    class GetConstrainValue {

        @Test
        @DisplayName("licenseService.getConstrainValue(tenantSeq) 로 위임")
        void delegatesToLicenseService() {
            given(licenseService.getConstrainValue(TENANT_A)).willReturn(1000);

            int result = seatLicenseService.getConstrainValue(TENANT_A);

            assertThat(result).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("isSeatAvailable(tenantSeq) / isSeatAvailable(tenantSeq, num) - 통합 시트 게이트")
    class IsSeatAvailable {

        @Test
        @DisplayName("비-SEAT 라이선스 → 추가 인원과 무관하게 항상 true (countActiveUsers 호출 안 함)")
        void nonSeat_alwaysTrue_doesNotQueryActiveUserCount() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(nonSeat());

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A)).isTrue();
            assertThat(seatLicenseService.isSeatAvailable(TENANT_A, 100)).isTrue();
            assertThat(seatLicenseService.isSeatAvailable(TENANT_A, 999999)).isTrue();

            // SEAT 가 아니면 active user count 조회 자체가 불필요
            then(seatLicenseRepository).should(never()).countActiveUsers(org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("SEAT - constrain(1000), active(500), 추가 0 → true")
        void seat_hasRoom_returnsTrue() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(500);

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A)).isTrue();
        }

        @Test
        @DisplayName("SEAT - constrain(1000), active(1000), 추가 0 → true (boundary)")
        void seat_atLimit_returnsTrue() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(1000);

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A)).isTrue();
        }

        @Test
        @DisplayName("SEAT - constrain(1000), active(1001) → false")
        void seat_overLimit_returnsFalse() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(1001);

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A)).isFalse();
        }

        @Test
        @DisplayName("SEAT - constrain(1000), active(500), 추가 500 → true (boundary)")
        void seat_exactBoundaryWithNum_returnsTrue() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(500);

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A, 500)).isTrue();
        }

        @Test
        @DisplayName("SEAT - constrain(1000), active(500), 추가 501 → false")
        void seat_wouldOverflow_returnsFalse() {
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(500);

            assertThat(seatLicenseService.isSeatAvailable(TENANT_A, 501)).isFalse();
        }
    }

    @Nested
    @DisplayName("getLicenseStateMap(tenantSeq)")
    class GetLicenseStateMap {

        @Test
        @DisplayName("SEAT + 시트 여유 → licenseService 상태 맵 그대로 반환")
        void seatAvailable_returnsLicenseServiceStateMapAsIs() throws LicenseValidatorException {
            Map<String, Object> baseStateMap = new HashMap<>();
            baseStateMap.put(Constants.LICENSE.STATE, true);
            baseStateMap.put(Constants.LICENSE.DAYS, 365);

            given(licenseService.getLicenseStateMap(TENANT_A)).willReturn(baseStateMap);
            given(licenseService.getConstrainValue(TENANT_A)).willReturn(1000);
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(500);

            Map result = seatLicenseService.getLicenseStateMap(TENANT_A);

            assertThat(result.get(Constants.LICENSE.STATE)).isEqualTo(true);
            assertThat(result).doesNotContainKey("code");
            assertThat(result).doesNotContainKey("currentSeatNum");
        }

        @Test
        @DisplayName("SEAT + 시트 초과 → state=false, EXCEED_LIMIT 코드와 시트 통계 추가")
        void seatExceeded_addsOverflowInfo() throws LicenseValidatorException {
            Map<String, Object> baseStateMap = new HashMap<>();
            baseStateMap.put(Constants.LICENSE.STATE, true);
            baseStateMap.put(Constants.LICENSE.DAYS, 365);

            given(licenseService.getLicenseStateMap(TENANT_A)).willReturn(baseStateMap);
            given(licenseService.getConstrainValue(TENANT_A)).willReturn(1000);
            given(licenseService.getSeatPolicy(TENANT_A)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_A)).willReturn(1500);

            Map result = seatLicenseService.getLicenseStateMap(TENANT_A);

            assertThat(result.get(Constants.LICENSE.STATE)).isEqualTo(false);
            assertThat(result.get("code")).isEqualTo(ResultCode.LICENSE_EXCEED_LIMIT.getCode());
            assertThat(result.get("currentSeatNum")).isEqualTo(1500);
            assertThat(result.get("constrainValue")).isEqualTo(1000);
            assertThat(result.get("availableSeatNum")).isEqualTo(-500);
        }

        @Test
        @DisplayName("licenseService.getLicenseStateMap 호출 시 같은 tenantSeq 전달")
        void delegatesWithSameTenantSeq() throws LicenseValidatorException {
            Map<String, Object> baseStateMap = new HashMap<>();
            baseStateMap.put(Constants.LICENSE.STATE, true);

            given(licenseService.getLicenseStateMap(TENANT_B)).willReturn(baseStateMap);
            given(licenseService.getConstrainValue(TENANT_B)).willReturn(1000);
            given(licenseService.getSeatPolicy(TENANT_B)).willReturn(seat(1000));
            given(seatLicenseRepository.countActiveUsers(TENANT_B)).willReturn(500);

            seatLicenseService.getLicenseStateMap(TENANT_B);

            then(licenseService).should(times(1)).getLicenseStateMap(TENANT_B);
        }
    }
}
