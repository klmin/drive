package com.thinkfree.drive.global.jpa.audit;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 감사 상위 클래스 1단 — 등록일(register_date)만.
 * <p>
 * persist 때 Spring Data JPA Auditing 이 현재 시각(초 절삭, {@link AuditConfig})으로 채운다(엔티티에 넣은 값도 덮어씀).
 * 등록자까지 있으면 {@link BaseRegisterEntity}, 수정자·수정일까지 있으면 {@link BaseAuditEntity}.
 */
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseRegisterTimeEntity {

    @CreatedDate
    private Instant registerDate;

    /** JdbcClient 쓰기 경로(upsert·배치)가 바인딩한 감사 값을 엔티티에 되써 넣는다 — 호출부가 저장 직후 읽는 경우. */
    public void assignRegisterDate(Instant registerDate) {
        this.registerDate = registerDate;
    }
}
