package com.thinkfree.drive.basedomain.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * tb_base_domain — 베이스 도메인(단일 베이스 운영: base_domain_seq 1 고정, CHECK singleton). PK 는 할당형 {@code @Id}.
 * 조회는 JPQL 생성자 식({@code DefaultBaseDomainQueryRepository} — BaseDomainData), 쓰기는 upsert(JdbcClient)뿐. 감사 4필드는 {@link BaseAuditEntity}. 변경 메서드 없음.
 */
@SuperBuilder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_base_domain")
public class BaseDomain extends BaseAuditEntity {

    @Id
    private Integer baseDomainSeq;
    /** 호스트만(소문자, 포트 없음) — 테넌트 도메인 조합 재료 */
    private String baseDomain;
    /** 아웃바운드 스킴(http|https). null 이면 요청 스킴, 요청이 없으면 https */
    private String scheme;
    /** 아웃바운드 포트. null 이면 생략 */
    private Integer port;
}
