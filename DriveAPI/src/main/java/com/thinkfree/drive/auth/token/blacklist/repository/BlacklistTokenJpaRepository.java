package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * tb_blacklist_token Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultBlacklistTokenQueryRepository}(읽기)·{@link DefaultBlacklistTokenRepository}(쓰기) 만 주입받아 위임한다.
 * 존재 확인은 파생 {@code existsBy}(token_hash UNIQUE), 삭제 2종은 JPQL {@code @Modifying} — 사용자 목록·만료 정리 조건부 다건이라 변경 감지 대상이 아님(파생 삭제는 SELECT 후 건별 DELETE 라 쓰지 않음. 리포 기본 트랜잭션이 readOnly 라 메서드에 {@code @Transactional} 필수).
 */
interface BlacklistTokenJpaRepository extends JpaRepository<BlacklistToken, Long> {

    @Query("select count(t) > 0 from BlacklistToken t where t.tokenHash = :tokenHash")
    boolean existsByTokenHash(@Param("tokenHash") String tokenHash);

    /** 아직 만료되지 않은 항목 — Redis 재적재(RedisBlacklistStore) */
    @Query("select new com.thinkfree.drive.auth.token.blacklist.query.BlacklistEntry(t.tokenHash, t.expirationDate) from BlacklistToken t where t.expirationDate > :now")
    List<BlacklistEntry> findAllNotExpired(@Param("now") Instant now);

    /** 사용자 삭제·탈퇴 시 해당 사용자들의 블랙리스트 정리 — 빈 목록 가드는 호출부(Default 리포) */
    @Transactional
    @Modifying
    @Query("delete from BlacklistToken t where t.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") List<Integer> userSeqList);

    /** 만료된 토큰 일괄 삭제(스케줄러) — expiration_date < 기준 */
    @Transactional
    @Modifying
    @Query("delete from BlacklistToken t where t.expirationDate < :expirationThreshold")
    int deleteByExpirationDateBefore(@Param("expirationThreshold") Instant expirationThreshold);
}
