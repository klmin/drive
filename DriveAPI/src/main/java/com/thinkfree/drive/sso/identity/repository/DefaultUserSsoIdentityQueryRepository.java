package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.query.UserSsoIdentityFindQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * tb_user_sso_identity 읽기 — 구 UserSsoIdentityMapper.xml 의 findWithUser(TB_USER INNER JOIN) 를 JPQL 생성자 식으로(§1 v2 "JPA 우선", JdbcClient 0). 쓰기는 {@link DefaultUserSsoIdentityRepository}.
 * DTO 투영이라 영속 컨텍스트에 엔티티가 남지 않음 → detach 불필요.
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserSsoIdentityQueryRepository implements UserSsoIdentityQueryRepository {

    private final UserSsoIdentityJpaRepository jpaRepository;

    @Override
    public UserSsoIdentityData findWithUser(UserSsoIdentityFindQuery query) {
        return jpaRepository.findWithUser(query.getSsoProviderSeq(), query.getTenantSeq(), query.getSubject()).orElse(null);
    }
}
