package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.userlog.data.UserLogData;
import com.thinkfree.drive.userlog.entity.UserLog;
import com.thinkfree.drive.userlog.enums.ActivityType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * tb_user_log Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultUserLogQueryRepository}(읽기)·{@link DefaultUserLogRepository}(쓰기) 만 주입받아 위임한다.
 * <ul>
 *   <li>{@code findAll} = JPQL 생성자 식 + {@link Limit}(구 XML 의 {@code LIMIT #{ps}}) — 커서 {@code ci} 가 null 이면 조건 생략(구 {@code <if>} 동형), {@code register_date DESC, user_log_seq DESC}(동점 처리 — 구 XML 은 register_date 만이었으나 같은 시각이면 순서가 불안정)</li>
 *   <li>{@code existsByUserSeqAndActivityType} = 파생(구 {@code COUNT(1) … LIMIT 1} 자리)</li>
 * </ul>
 */
interface UserLogJpaRepository extends JpaRepository<UserLog, Long> {

    @Query("select new com.thinkfree.drive.userlog.data.UserLogData(l.userLogSeq, l.userSeq, l.region, l.ipAddress, l.activityType, l.actorType, l.registerDate) "
            + "from UserLog l where l.userSeq = :userSeq and (:ci is null or l.userLogSeq < :ci) order by l.registerDate desc, l.userLogSeq desc")
    List<UserLogData> findAll(@Param("userSeq") Integer userSeq, @Param("ci") Long ci, Limit limit);

    @Query("select count(l) > 0 from UserLog l where l.userSeq = :userSeq and l.activityType = :activityType")
    boolean existsByUserSeqAndActivityType(@Param("userSeq") Integer userSeq, @Param("activityType") ActivityType activityType);
}
