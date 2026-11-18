package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;

import java.time.Instant;
import java.util.List;

/**
 * tb_blacklist_token 쓰기 — {@code DefaultBlacklistTokenService}(로그아웃 save, 스케줄러·사용자 삭제의 삭제 2종) 가 주입. 읽기는 {@link BlacklistTokenQueryRepository}.
 */
public interface BlacklistTokenRepository {

    /** JPA save(IDENTITY 라 저장 순간 INSERT) — identity 키·register_date 가 엔티티에 채워져 그 인스턴스를 돌려준다 */
    BlacklistToken save(BlacklistToken entity);

    /** 빈/널 목록은 SQL 없이 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);

    int deleteExpiredTokens(Instant expirationThreshold);
}
