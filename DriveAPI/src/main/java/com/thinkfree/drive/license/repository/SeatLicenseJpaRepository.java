package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

/**
 * SEAT 좌석 집계용 tb_user Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSeatLicenseRepository} 만 주입받아 위임한다.
 * 구 PerSeatLicenseMapper.countActiveUsers(테넌트의 STATUS='ACTIVE' 수)를 파생 count 쿼리로. user 패키지의 {@code UserJpaRepository} 는 패키지 전용이라 같은 엔티티에 대한 별도 선언을 둔다(쓰기 없음).
 */
interface SeatLicenseJpaRepository extends JpaRepository<User, Integer> {

    @Query("select count(u) from User u where u.tenantSeq = :tenantSeq and u.status = :status")
    long countByTenantSeqAndStatus(@Param("tenantSeq") Integer tenantSeq, @Param("status") UserStatus status);
}
