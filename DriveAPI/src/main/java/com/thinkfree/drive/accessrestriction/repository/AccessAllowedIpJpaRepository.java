package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.data.AccessAllowedIpData;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * tb_access_allowed_ip Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultAccessAllowedIpQueryRepository}(읽기)·{@link DefaultAccessAllowedIpRepository}(쓰기) 만 주입받아 위임한다.
 * 조회는 JPQL 생성자 식 DTO(ip_address, cidr_prefix 오름차순 — 구 XML 동형), 테넌트+역할 삭제는 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 {@code @Transactional} 필수). N건 INSERT 는 JDBC 배치(Default 리포).
 */
interface AccessAllowedIpJpaRepository extends JpaRepository<AccessAllowedIp, Integer> {

    @Query("select new com.thinkfree.drive.accessrestriction.data.AccessAllowedIpData(i.ipAddress, i.cidrPrefix) from AccessAllowedIp i "
            + "where i.tenantSeq = :tenantSeq and i.roleType = :roleType order by i.ipAddress asc, i.cidrPrefix asc")
    List<AccessAllowedIpData> findAllByTenantSeqAndRoleType(@Param("tenantSeq") Integer tenantSeq, @Param("roleType") RoleType roleType);

    @Transactional
    @Modifying
    @Query("delete from AccessAllowedIp i where i.tenantSeq = :tenantSeq and i.roleType = :roleType")
    int deleteByTenantSeqAndRoleType(@Param("tenantSeq") Integer tenantSeq, @Param("roleType") RoleType roleType);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from AccessAllowedIp a where a.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
