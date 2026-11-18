package com.thinkfree.drive.oauth2.jwk.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 유효한 JWK 한 건 — 읽기 전용 값(OAuth2KeyManager 캐시 reload 용). expiration_date NULL = 현역 서명 키. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(OAuth2JwkJpaRepository#findAllValid)
public class OAuth2JwkData {

    private String kid;
    private String jwkJson;
    private Instant expirationDate;

    public boolean isSigning() {
        return expirationDate == null;
    }
}
