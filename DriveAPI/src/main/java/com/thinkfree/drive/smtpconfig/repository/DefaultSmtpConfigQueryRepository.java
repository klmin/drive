package com.thinkfree.drive.smtpconfig.repository;

import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * tb_smtp_config 읽기 — JPQL 생성자 식(§1 v2 "JPA 우선", JdbcClient 0). 쓰기는 {@link DefaultSmtpConfigRepository}.
 * 결과는 {@link SmtpConfigData} — 엔티티를 영속 컨텍스트에 올리지 않으므로 항상 DB 현재 값이고 쓰기 흐름의 관리 엔티티와 섞이지 않는다(detach 불필요).
 */
@Repository
@RequiredArgsConstructor
public class DefaultSmtpConfigQueryRepository implements SmtpConfigQueryRepository {

    private final SmtpConfigJpaRepository jpaRepository;

    /** PK 한 행 + 테넌트 Java 판정 — seq·tenant 가 null 이거나 다른 테넌트 행이면 null(없음과 같음) */
    @Override
    public SmtpConfigData findBySmtpConfigSeq(Integer smtpConfigSeq, Integer tenantSeq) {
        if (smtpConfigSeq == null || tenantSeq == null) {
            return null;
        }
        return jpaRepository.findDataBySmtpConfigSeq(smtpConfigSeq).filter(d -> tenantSeq.equals(d.getTenantSeq())).orElse(null);
    }

    @Override
    public SmtpConfigData findByTenant(Integer tenantSeq) {
        return jpaRepository.findDataByTenantSeq(tenantSeq).orElse(null);
    }
}
