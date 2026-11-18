package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.SeatLicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeatLicenseService {
    private final SeatLicenseRepository seatLicenseRepository;
    private final LicenseService licenseService;

    /*
     동시성 이슈로 해당 기능은 라이선스 정보 표시용으로 사용.
     사용자 등록, 활성 사용자 변경은 조건부 쿼리로 실행할 것.
    */
    public int getCurrentActiveUserNum(int tenantSeq) {
        return seatLicenseRepository.countActiveUsers(tenantSeq);
    }

    public int getConstrainValue(int tenantSeq) {
        return licenseService.getConstrainValue(tenantSeq);
    }

    /**
     * "지금 이 테넌트에 N명 더 등록 가능한가?" 의 단일 게이트.
     * 비-SEAT 라이선스는 시트 제약이 없으므로 무조건 true.
     * 호출자가 isSeatLicense 를 별도로 체크할 필요 없도록 흡수.
     */
    public boolean isSeatAvailable(int tenantSeq) {
        return isSeatAvailable(tenantSeq, 0);
    }

    public boolean isSeatAvailable(int tenantSeq, int num) {
        var policy = licenseService.getSeatPolicy(tenantSeq);
        if (!policy.isSeat()) return true;
        return policy.constrainValue() >= getCurrentActiveUserNum(tenantSeq) + num;
    }


    /**
     * PAU(SEAT) 라이선스 상태 정보를 반환한다.
     *
     * 반환되는 Map 구조:
     * {
     *   "state": Boolean,                  // 라이선스 유효 여부
     *   "days" : Integer,                  // 라이선스 유효 기간
     *   "spents": Integer,                 // 경과 일수
     *   "expiresOn": String,                // 라이선스 만료일 (yyyy-mm-dd)
     *   "resultCode": ResultCode,           // 사용량(PAU)에 따른 결과 코드 (조건에 따라 존재)
     *       - LICENSE_EXCEED_LIMIT : 사용량 초과
     *       - LICENSE_UNDER_LIMIT  : 사용량 여유 있음 (0 ~ 10 구간)
     *       "currentActiveUser"                // 현재 활성화된 SEAT 사용자 수
     * }
     *
     * @return 라이선스 상태 Map
     * @throws LicenseValidatorException 라이선스 검증 실패 시
     */
    public Map<String, Object> getLicenseStateMap(int tenantSeq) throws LicenseValidatorException {
        Map<String, Object> licenseStateMap = licenseService.getLicenseStateMap(tenantSeq);
        int currentSeatNum = getCurrentActiveUserNum(tenantSeq);
        int constrainValue = getConstrainValue(tenantSeq);

        if (!isSeatAvailable(tenantSeq)) {
            licenseStateMap.put(Constants.LICENSE.STATE, false);
            licenseStateMap.put("code", ResultCode.LICENSE_EXCEED_LIMIT.getCode());
            licenseStateMap.put(Constants.LICENSE.RESULT_MESSAGE, ResultCode.LICENSE_EXCEED_LIMIT);
            licenseStateMap.put("currentSeatNum", currentSeatNum);
            licenseStateMap.put("constrainValue", constrainValue);
            licenseStateMap.put("availableSeatNum", constrainValue - currentSeatNum);
        }

        return licenseStateMap;
    }
}
