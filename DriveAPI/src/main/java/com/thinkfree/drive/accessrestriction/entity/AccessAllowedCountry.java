package com.thinkfree.drive.accessrestriction.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_access_allowed_country — 테넌트별 접근 허용 국가(ISO 2자리, UNIQUE (tenant_seq, country_iso_code)). 전체 교체 방식(테넌트 삭제 → N건 INSERT).
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선"): 조회는 JPQL, 테넌트 삭제는 JPQL — {@code AccessAllowedCountryJpaRepository}. **N건 INSERT 만 JdbcClient 배치**({@code NamedParameterJdbcTemplate.batchUpdate}, 생성 키 불필요 — Hibernate 는 IDENTITY 를 배치하지 못함, §1 v2 "JdbcClient 잔존").
 * 등록 감사는 {@code create} 가 register_date 를 현재 시각으로 넣고 배치가 {@link #assignRegisterAudit} 로 바인딩 값을 write-back(비어 있으면 {@code JdbcAudit} 규칙). 변경 메서드 없는 불변 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_access_allowed_country")
public class AccessAllowedCountry extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer countrySeq;
    private Integer tenantSeq;
    private String countryIsoCode;

    private AccessAllowedCountry(Integer tenantSeq, String countryIsoCode, Integer registerUserSeq) {
        this.tenantSeq = tenantSeq;
        this.countryIsoCode = countryIsoCode;
        assignRegisterAudit(registerUserSeq, Instant.now()); // JPA persist 면 Auditing 이 덮어씀(배치 INSERT 는 리포가 JdbcAudit 로 다시 씀)
    }

    public static AccessAllowedCountry create(Integer tenantSeq, String countryIsoCode, Integer registerUserSeq) {
        return new AccessAllowedCountry(tenantSeq, countryIsoCode, registerUserSeq);
    }
}
