package com.thinkfree.drive.license.repository;

public interface SeatLicenseRepository {

    /** 테넌트의 ACTIVE 사용자 수(SEAT 좌석 사용량 표시용). */
    int countActiveUsers(int tenantSeq);
}
