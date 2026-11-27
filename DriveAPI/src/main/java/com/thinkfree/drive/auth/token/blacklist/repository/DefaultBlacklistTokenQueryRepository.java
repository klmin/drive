package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistEntry;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * tb_blacklist_token 읽기 — 구 BlacklistTokenMapper.xml 의 existsByTokenHash(SELECT EXISTS) 를 JPA 파생 {@code existsBy} 로(§1 v2 "JPA 우선", JdbcClient 0). 쓰기는 {@link DefaultBlacklistTokenRepository}.
 * 엔티티를 돌려주지 않으므로 detach 불필요.
 */
@Repository
@RequiredArgsConstructor
public class DefaultBlacklistTokenQueryRepository implements BlacklistTokenQueryRepository {

    private final BlacklistTokenJpaRepository jpaRepository;

    @Override
    public boolean existsByTokenHash(BlacklistFindQuery query) {
        return jpaRepository.existsByTokenHash(query.tokenHash());
    }

    @Override
    public List<BlacklistEntry> findAllNotExpired(Instant now) {
        return jpaRepository.findAllNotExpired(now);
    }
}
