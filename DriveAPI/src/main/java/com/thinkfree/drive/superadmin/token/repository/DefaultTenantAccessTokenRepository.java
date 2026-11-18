package com.thinkfree.drive.superadmin.token.repository;

import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * tb_tenant_access_token 쓰기 + 쓰기 흐름 안의 조회 — 구 TenantAccessTokenMapper.xml 의 5 statement 를 **전부 JPA** 로(§1 v2 "JPA 우선", JdbcClient 0 인 첫 리포).
 * <p>
 * 소비자 = {@code DefaultTenantAccessTokenService}(issue: deleteByTenantSeq → save / consume: findByTokenHash → deleteByTokenHash)·{@code auth/token/service/TokenService}(스케줄러 deleteExpiredTokens).
 * 읽기 전용 FindService 가 없어 Query 리포를 분리하지 않는다(findByTokenHash 는 소비 흐름 안의 읽기).
 * <ul>
 *   <li>{@code save} = Spring Data {@code save} — IDENTITY 라 저장 순간 INSERT, identity 키가 엔티티에 채워지고(구 XML {@code useGeneratedKeys}), register_date 는 Spring Data Auditing(현재 시각)</li>
 *   <li>{@code findByTokenHash} = JPQL, 없으면 null(기존 인터페이스 계약 유지). detach 하지 않음 — 같은 트랜잭션에 JdbcClient 쓰기가 없고 엔티티는 불변이라 관리 인스턴스가 남아도 flush 때 UPDATE 가 나가지 않는다</li>
 *   <li>삭제 3종 = JPQL {@code @Modifying}(로드 없이 1문, 이식 가능). JPQL 벌크 삭제는 영속 컨텍스트를 비우지 않지만 이 흐름은 삭제한 인스턴스를 다시 쓰지 않는다</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultTenantAccessTokenRepository implements TenantAccessTokenRepository {

    private final TenantAccessTokenJpaRepository jpaRepository;

    @Override
    public void save(TenantAccessToken entity) {
        jpaRepository.save(entity);
    }

    @Override
    public void deleteByTenantSeq(Integer tenantSeq) {
        jpaRepository.deleteByTenantSeq(tenantSeq);
    }

    @Override
    public TenantAccessToken findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).orElse(null);
    }

    @Override
    public int deleteByTokenHash(String tokenHash) {
        return jpaRepository.deleteByTokenHash(tokenHash);
    }

    @Override
    public int deleteExpiredTokens(Instant expirationThreshold) {
        return jpaRepository.deleteByExpirationDateBefore(expirationThreshold);
    }
}
