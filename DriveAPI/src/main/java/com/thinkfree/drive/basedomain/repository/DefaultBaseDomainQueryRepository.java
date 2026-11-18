package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.data.BaseDomainData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * tb_base_domain 읽기 — JPQL 생성자 식({@link BaseDomainJpaRepository})으로 {@link BaseDomainData} 를 바로 받는다.
 * 엔티티를 영속 컨텍스트에 올리지 않으므로 같은 트랜잭션의 upsert(JdbcClient) 뒤에도 항상 DB 현재 값(detach 불필요). 쓰기는 {@link DefaultBaseDomainRepository}.
 */
@Repository
@RequiredArgsConstructor
public class DefaultBaseDomainQueryRepository implements BaseDomainQueryRepository {

    /** 단일 베이스 운영 단계의 고정 PK. */
    private static final int SINGLE_BASE_DOMAIN_SEQ = 1;

    private final BaseDomainJpaRepository jpaRepository;

    @Override
    public BaseDomainData findCurrent() {
        return findByBaseDomainSeq(SINGLE_BASE_DOMAIN_SEQ);
    }

    @Transactional(readOnly = true)
    @Override
    public BaseDomainData findByBaseDomainSeq(Integer baseDomainSeq) {
        if (baseDomainSeq == null) {
            return null;
        }
        return jpaRepository.findData(baseDomainSeq).orElse(null);
    }
}
