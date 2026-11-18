package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;

/**
 * tb_blacklist_token 읽기 — {@code DefaultBlacklistTokenFindService}(JwtAuthenticationFilter·OAuth2SessionBridgeUseCase 의 매 요청 블랙리스트 확인) 가 주입. 쓰기는 {@link BlacklistTokenRepository}.
 */
public interface BlacklistTokenQueryRepository {

    boolean existsByTokenHash(BlacklistFindQuery query);
}
