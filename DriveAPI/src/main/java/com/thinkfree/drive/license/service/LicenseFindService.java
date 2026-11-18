package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.result.LicenseSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 라이선스 표시용 read-only 조회 전용.
 * 기존 {@link LicenseService} 는 등록/검증/캐시/Map 형태 노출까지 들고 있어 비대 — 화면 표시용
 * typed 응답은 이 서비스로 분리한다. per-tenant 라이선스 모델에 맞춰 tenantSeq 인자를 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseFindService {

    private final LicenseService licenseService;
    private final SeatLicenseService seatLicenseService;

    /**
     * 화면 표시용 라이선스 요약.
     * Why: 기존 Map 기반 API는 캐스팅이 지저분하고 키 의존성이 높아, 슈퍼어드민 테넌트 목록처럼
     *      여러 row 에 동일 정보를 채워야 하는 화면에서 타입 안전한 단일 호출이 필요.
     * 미등록/검증 실패 시 null 을 반환 — 호출 측에서 "라이선스 없음" 으로 처리.
     */
    public LicenseSummary getSummary(int tenantSeq) {
        try {
            Map<String, Object> license = licenseService.getLicense(tenantSeq);
            if (license == null) {
                return null;
            }

            boolean state = (boolean) license.get(Constants.LICENSE.STATE);
            String expiresOn = (String) license.get(Constants.LICENSE.EXPIRES_ON);
            String licenseType = (String) license.get(LicenseInfos.LICENSE_TYPE);

            LicenseSummary.SeatSummary seat = null;

            if (Constants.LICENSE.LICENSE_TYPE_SEAT.equals(licenseType)) {
                int current = seatLicenseService.getCurrentActiveUserNum(tenantSeq); // 현재 활성화된 유저 수
                int max = seatLicenseService.getConstrainValue(tenantSeq);           // 최대 활성화 유저 수
                seat = new LicenseSummary.SeatSummary(current, max);
            }

            return new LicenseSummary(state, licenseType, expiresOn, seat);
        } catch (LicenseValidatorException e) {
            log.warn("[ LicenseFindService.getSummary ] license validation failed: tenantSeq={}, {}", tenantSeq, e.getMessage());
            return null;
        }
    }
}
