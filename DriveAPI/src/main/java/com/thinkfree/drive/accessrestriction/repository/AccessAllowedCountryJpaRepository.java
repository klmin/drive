package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * tb_access_allowed_country Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultAccessAllowedCountryQueryRepository}(읽기)·{@link DefaultAccessAllowedCountryRepository}(쓰기) 만 주입받아 위임한다.
 * 조회는 JPQL 스칼라(국가 코드 오름차순 — 엔티티를 영속성 컨텍스트에 올리지 않음), 테넌트 삭제는 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 {@code @Transactional} 필수). N건 INSERT 는 JDBC 배치(Default 리포).
 */
interface AccessAllowedCountryJpaRepository extends JpaRepository<AccessAllowedCountry, Integer> {

    @Query("select c.countryIsoCode from AccessAllowedCountry c where c.tenantSeq = :tenantSeq order by c.countryIsoCode asc")
    List<String> findCountryIsoCodesByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    @Transactional
    @Modifying
    @Query("delete from AccessAllowedCountry c where c.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
