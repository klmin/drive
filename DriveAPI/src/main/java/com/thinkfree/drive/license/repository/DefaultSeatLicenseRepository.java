package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * SEAT 좌석 사용량 — 구 PerSeatLicenseMapper.countActiveUsers(tb_user STATUS='ACTIVE' 수)를 JPA 파생 count 로({@link SeatLicenseJpaRepository}). 표시용 집계(동시성 가드는 사용자 등록 쪽 조건부 INSERT).
 */
@Repository
@RequiredArgsConstructor
public class DefaultSeatLicenseRepository implements SeatLicenseRepository {

    private final SeatLicenseJpaRepository jpaRepository;

    @Override
    public int countActiveUsers(int tenantSeq) {
        return (int) jpaRepository.countByTenantSeqAndStatus(tenantSeq, UserStatus.ACTIVE);
    }
}
