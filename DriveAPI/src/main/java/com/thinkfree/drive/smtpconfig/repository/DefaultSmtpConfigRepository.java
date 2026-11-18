package com.thinkfree.drive.smtpconfig.repository;

import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * tb_smtp_config 쓰기 — JPA(§1 v2 "JPA 우선", JdbcClient 0). 읽기는 {@link DefaultSmtpConfigQueryRepository}.
 * <ul>
 *   <li>{@code save} = IDENTITY 키라 저장 순간 INSERT·id 채움(register_user_seq 는 엔티티 선언 값, register_date 는 Auditing, update_date 는 {@code insertable=false} 라 DB DEFAULT)</li>
 *   <li>{@code findById} = PK 로드(관리 엔티티, 테넌트 판정은 Service — 10-13). 수정은 서비스가 변경 메서드로 하고 커밋 때 변경 감지 — 이 리포에 update 메서드는 없다</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultSmtpConfigRepository implements SmtpConfigRepository {

    private final SmtpConfigJpaRepository jpaRepository;

    @Override
    public void save(SmtpConfig smtpConfig) {
        jpaRepository.save(smtpConfig);
    }

    @Override
    public SmtpConfig findById(int smtpConfigSeq) {
        return jpaRepository.findById(smtpConfigSeq).orElse(null);
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenantSeq(tenantSeq);
    }
}
