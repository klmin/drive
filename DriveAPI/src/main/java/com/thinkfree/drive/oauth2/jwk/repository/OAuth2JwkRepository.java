package com.thinkfree.drive.oauth2.jwk.repository;

import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;

import java.time.Instant;

/**
 * tb_oauth2_jwk 쓰기 — {@code DefaultOAuth2JwkService}(최초 키 저장·회전·만료 삭제) 가 주입. 읽기는 {@link OAuth2JwkQueryRepository}.
 */
public interface OAuth2JwkRepository {

    /** 할당형 PK(kid) INSERT — 실제 INSERT 는 flush·커밋 때. 중복 kid 는 그때 DataAccessException(동시 기동 경쟁은 호출부가 DataAccessException 으로 흡수) */
    void save(OAuth2Jwk jwk);

    /** 현역 키 전부를 검증 전용으로 강등(expiration_date 설정) — 강등 건수 */
    int expireSigningKeys(Instant expirationDate);

    /** 유예기간이 끝난 구 키 삭제 — 삭제 건수 */
    int deleteExpired();
}
