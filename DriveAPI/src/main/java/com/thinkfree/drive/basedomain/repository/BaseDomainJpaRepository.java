package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.entity.BaseDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * tb_base_domain Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultBaseDomainQueryRepository} 만 주입받아 위임한다(JPQL 생성자 식).
 * 쓰기(upsert)는 JdbcClient({@link DefaultBaseDomainRepository}).
 */
interface BaseDomainJpaRepository extends JpaRepository<BaseDomain, Integer> {

    /** PK 한 행 — DTO 로 받아 엔티티를 영속 컨텍스트에 올리지 않는다 */
    @Query("select new com.thinkfree.drive.basedomain.data.BaseDomainData(b.baseDomainSeq, b.baseDomain, b.scheme, b.port) from BaseDomain b where b.baseDomainSeq = :baseDomainSeq")
    Optional<BaseDomainData> findData(@Param("baseDomainSeq") Integer baseDomainSeq);
}
