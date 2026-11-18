package com.thinkfree.drive.global.jpa.audit;

import com.thinkfree.drive.global.jdbc.JdbcAudit;

import java.time.Instant;

/**
 * JPQL {@code @Modifying} UPDATE 의 수정 감사 값(update_user_seq·update_date) 묶음 — 벌크 UPDATE 는 Spring Data Auditing 을 타지 않아 리포가 정해 넘긴다.
 * 규칙은 {@link JdbcAudit} 그대로(시큐리티 사용자 우선, 없으면 선언 값 / 선언 시각, 없으면 현재 시각 초 절삭).
 * <p>
 * JPQL 은 파라미터 하나({@code @Param("stamp")})로 받아 SpEL 로 꺼낸다: {@code x.updateUserSeq = :#{#stamp.userSeq}, x.updateDate = :#{#stamp.date}}.
 * 롤백·복원처럼 이전 값을 되돌리는 쓰기와 호출부 값을 그대로 쓰는 경로는 이 타입을 쓰지 않는다.
 */
public record UpdateStamp(Integer userSeq, Instant date) {

    /** 선언 값에 감사 규칙 적용 — 사용자는 {@link JdbcAudit#userSeqOr}, 시각은 {@link JdbcAudit#nowOr}. */
    public static UpdateStamp of(Integer declaredUserSeq, Instant declaredDate) {
        return new UpdateStamp(JdbcAudit.userSeqOr(declaredUserSeq), JdbcAudit.nowOr(declaredDate));
    }
}
