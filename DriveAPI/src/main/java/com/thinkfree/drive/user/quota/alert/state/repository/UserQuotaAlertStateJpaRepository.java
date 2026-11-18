package com.thinkfree.drive.user.quota.alert.state.repository;

import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * tb_user_quota_alert_state Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultUserQuotaAlertStateRepository} 만 주입받아 위임한다.
 * <ul>
 *   <li>잠금은 스칼라 투영 + {@code @Lock(PESSIMISTIC_WRITE)}(SELECT … FOR UPDATE) — 엔티티를 영속성 컨텍스트에 올리지 않아, 같은 행을 이어서 쓰는 jOOQ upsert 와 섞이지 않는다(detach 불필요, W7)</li>
 *   <li>IN 삭제는 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 {@code @Transactional} 필수)</li>
 * </ul>
 * INSERT/UPDATE 는 없다 — 전부 jOOQ upsert(Default 리포).
 */
interface UserQuotaAlertStateJpaRepository extends JpaRepository<UserQuotaAlertState, Integer> {

    /** 호출부 트랜잭션 안에서만(잠금은 커밋까지), 행이 없으면 empty — 잠글 행이 없다(첫 기록 경쟁은 upsert ON CONFLICT 가 흡수) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s.lastThresholdPercent from UserQuotaAlertState s where s.userSeq = :userSeq")
    Optional<Integer> lockLastThresholdPercentByUserSeq(@Param("userSeq") Integer userSeq);

    @Transactional
    @Modifying
    @Query("delete from UserQuotaAlertState s where s.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") List<Integer> userSeqList);
}
