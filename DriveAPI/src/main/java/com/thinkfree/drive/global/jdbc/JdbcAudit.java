package com.thinkfree.drive.global.jdbc;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.util.SecurityUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JdbcClient 경로의 감사 컬럼(register/update user·date) 값 결정 — JPA 엔티티의 Spring Data Auditing({@code AuditConfig})과 같은 규칙.
 * <ul>
 *   <li>사용자: 시큐리티 사용자가 있으면 그 사용자(선언 값을 덮어씀). 없으면(스케줄러·웹오피스 어댑터 등 시스템 작업) 선언 값 그대로 — 그것도 없으면 null 이라 NOT NULL 컬럼은 DB 가 거부한다.</li>
 *   <li>시각: 선언 값이 없으면 현재 시각 초 절삭.</li>
 * </ul>
 * 롤백·복원처럼 이전 값을 되돌리는 쓰기는 이 클래스를 거치지 않고 값을 직접 바인딩한다(예: {@code revertOverwrite}, {@code rollbackResourceVersionData}).
 * 값만 돌려주므로 리포가 바인딩에 쓰고, 호출부가 저장 직후 감사 필드를 읽는 도메인은 리포가 같은 값을 엔티티에 되써 넣는다.
 * JPQL {@code @Modifying} UPDATE 는 이 규칙을 감싼 {@link UpdateStamp} 하나로 넘긴다(update_user_seq·update_date 를 같이 쓰는 문).
 */
public final class JdbcAudit {

    private JdbcAudit() {
    }

    /** 감사 사용자 — 시큐리티 사용자, 없으면 선언 값. */
    public static Integer userSeqOr(Integer declared) {
        Integer current = currentUserSeq();
        return current != null ? current : declared;
    }

    /** 시큐리티 사용자({@code CustomUserDetails}) seq. 없으면 null. */
    public static Integer currentUserSeq() {
        return SecurityUtil.getUserSeq();
    }

    public static Instant nowOr(Instant existing) {
        return existing != null ? existing : now();
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
