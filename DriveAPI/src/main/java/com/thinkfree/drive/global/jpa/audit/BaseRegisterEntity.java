package com.thinkfree.drive.global.jpa.audit;

import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;

import java.time.Instant;

/**
 * 감사 상위 클래스 2단 — 등록일 + 등록자(register_user_seq).
 * <p>
 * 등록자는 시큐리티 사용자가 있으면 그 사용자로 덮어쓰고, 없으면(스케줄러 등 시스템 작업) 엔티티에 선언한 값을 그대로 둔다({@link AuditConfig}).
 */
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class BaseRegisterEntity extends BaseRegisterTimeEntity {

    @CreatedBy
    private Integer registerUserSeq;

    /** JdbcClient 쓰기 경로의 등록 감사 값 write-back. */
    public void assignRegisterAudit(Integer registerUserSeq, Instant registerDate) {
        this.registerUserSeq = registerUserSeq;
        assignRegisterDate(registerDate);
    }
}
