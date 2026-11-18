package com.thinkfree.drive.superadmin.token.repository;

import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * tb_tenant_access_token Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultTenantAccessTokenRepository} 만 주입받아 위임한다.
 * 조회는 파생 쿼리(token_hash UNIQUE·NOT NULL → 단건, 10-13), 삭제 3종은 JPQL {@code @Modifying} — 테넌트·사용자별·만료 정리는 조건부 다건, 토큰 해시 삭제는 1회용 원자 판정이라 변경 감지 대상이 아님(파생 삭제 {@code deleteBy…} 는 SELECT 후 건별 DELETE 라 쓰지 않음.
 * 리포 기본 트랜잭션이 readOnly 라 메서드에 {@code @Transactional} 필수 — PG 는 읽기 전용 트랜잭션의 DELETE 를 거부).
 */
interface TenantAccessTokenJpaRepository extends JpaRepository<TenantAccessToken, Integer> {

    Optional<TenantAccessToken> findByTokenHash(String tokenHash);

    /** 동일 테넌트의 기존 토큰 삭제(중복 발급 방지) */
    @Transactional
    @Modifying
    @Query("delete from TenantAccessToken t where t.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 인증 완료 후 토큰 즉시 삭제 — 삭제 행 수(0 = 이미 소비)로 1회용을 원자적으로 판정하므로 로드 후 삭제(변경 감지)로 바꾸지 않는다 */
    @Transactional
    @Modifying
    @Query("delete from TenantAccessToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /** 만료된 토큰 일괄 삭제(스케줄러) — expiration_date < 기준 */
    @Transactional
    @Modifying
    @Query("delete from TenantAccessToken t where t.expirationDate < :expirationThreshold")
    int deleteByExpirationDateBefore(@Param("expirationThreshold") Instant expirationThreshold);
}
