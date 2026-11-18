package com.thinkfree.drive.auth.token.refresh.repository;

import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * tb_refresh_token Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultRefreshTokenRepository} 만 주입받아 위임한다.
 * 조회는 파생 쿼리(UNIQUE·NOT NULL session_id 라 0/1건 — 재발급용 관리 엔티티), RTR 갱신은 서비스의 변경 감지(W2 10-12), 삭제 4종은 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 {@code @Transactional} 필수, 대기 중 쓰기는 먼저 flush).
 */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findBySessionId(String sessionId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userSeq = :userSeq")
    int deleteByUserSeq(@Param("userSeq") int userSeq);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userSeq in :userSeqs")
    int deleteByUserSeqIn(@Param("userSeqs") Collection<Integer> userSeqs);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expirationDate < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
