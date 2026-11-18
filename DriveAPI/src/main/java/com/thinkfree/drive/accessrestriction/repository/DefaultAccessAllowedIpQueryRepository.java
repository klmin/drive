package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.data.AccessAllowedIpData;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_access_allowed_ip 읽기 — 구 AccessAllowedIpMapper.xml 의 findAllByTenantAndRole 을 JPA(JPQL)로(§1 v2 "JPA 우선"). 쓰기는 {@link DefaultAccessAllowedIpRepository}.
 * 결과는 JPQL 생성자 식 DTO — 엔티티를 영속 컨텍스트에 올리지 않으므로 같은 서비스의 교체 흐름(JPQL 삭제 → JDBC 배치 INSERT)과 섞이지 않는다(detach 불필요).
 */
@Repository
@RequiredArgsConstructor
public class DefaultAccessAllowedIpQueryRepository implements AccessAllowedIpQueryRepository {

    private final AccessAllowedIpJpaRepository jpaRepository;

    @Override
    public List<AccessAllowedIpData> findAllByTenantAndRole(Integer tenantSeq, RoleType roleType) {
        return jpaRepository.findAllByTenantSeqAndRoleType(tenantSeq, roleType);
    }
}
