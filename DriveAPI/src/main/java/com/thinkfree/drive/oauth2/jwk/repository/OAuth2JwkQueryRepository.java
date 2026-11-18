package com.thinkfree.drive.oauth2.jwk.repository;

import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;

import java.util.List;

/**
 * tb_oauth2_jwk 읽기 — {@code DefaultOAuth2JwkFindService}(OAuth2KeyManager 의 캐시 reload) 가 주입. 쓰기는 {@link OAuth2JwkRepository}.
 */
public interface OAuth2JwkQueryRepository {

    /** 유효한 키 전부(현역 + 유예기간 중 구 키) — 최신 우선 정렬(register_date DESC, kid DESC) */
    List<OAuth2JwkData> findAllValid();
}
