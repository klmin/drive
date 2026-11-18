package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * tb_blacklist_token 쓰기 — 구 BlacklistTokenMapper.xml 의 save·deleteByUserSeqList·deleteExpiredTokens 를 전부 JPA 로(§1 v2 "JPA 우선", JdbcClient 0). 읽기는 {@link DefaultBlacklistTokenQueryRepository}.
 * <ul>
 *   <li>{@code save} = Spring Data {@code save} — IDENTITY 라 저장 순간 INSERT, identity 키가 엔티티에 채워지고(구 XML 은 키를 돌려주지 않았음), register_date 는 Spring Data Auditing(현재 시각)</li>
 *   <li>삭제 2종 = JPQL {@code @Modifying}. IN 목록이 비면 SQL 없이 0(구 foreach 는 {@code IN ()} 문법 오류)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultBlacklistTokenRepository implements BlacklistTokenRepository {

    private final BlacklistTokenJpaRepository jpaRepository;

    @Override
    public BlacklistToken save(BlacklistToken entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteExpiredTokens(Instant expirationThreshold) {
        return jpaRepository.deleteByExpirationDateBefore(expirationThreshold);
    }
}
