package com.thinkfree.drive.accessrestriction.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_access_allowed_country 읽기 — 구 AccessAllowedCountryMapper.xml 의 findAllByTenant 를 JPA(JPQL)로(§1 v2 "JPA 우선"). 쓰기는 {@link DefaultAccessAllowedCountryRepository}.
 * 결과는 국가 코드 스칼라 — 엔티티를 영속 컨텍스트에 올리지 않으므로 같은 서비스의 교체 흐름(JPQL 삭제 → JDBC 배치 INSERT)과 섞이지 않는다(detach 불필요).
 * 구 XML 의 {@code existsByTenantAndCountryIsoCode} 는 매퍼 인터페이스에 없던 죽은 statement → 옮기지 않음.
 */
@Repository
@RequiredArgsConstructor
public class DefaultAccessAllowedCountryQueryRepository implements AccessAllowedCountryQueryRepository {

    private final AccessAllowedCountryJpaRepository jpaRepository;

    @Override
    public List<String> findAllCountryCodes(Integer tenantSeq) {
        return jpaRepository.findCountryIsoCodesByTenantSeq(tenantSeq);
    }
}
