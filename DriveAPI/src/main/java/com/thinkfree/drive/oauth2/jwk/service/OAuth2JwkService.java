package com.thinkfree.drive.oauth2.jwk.service;

import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;

import java.time.Instant;

public interface OAuth2JwkService {

    /** 신규 키 저장 (최초 기동 시 현역 키 생성용) */
    void save(OAuth2Jwk jwk);

    /**
     * 키 회전의 DB 반영 — 현역 키 전부를 검증 전용으로 강등(expiration_date 설정)하고
     * 새 현역 키를 저장한다. 두 문장이 원자적이어야 한다 — 강등만 성공하고 저장이
     * 실패하면 현역 키 0개가 되어 토큰 발급이 전면 중단되므로 한 트랜잭션으로 묶는다.
     */
    void rotateSigningKey(Instant demotedKeyExpirationDate, OAuth2Jwk newKey);

    /** 유예기간이 지난 폐기 키 삭제 — 삭제 건수 반환 */
    int deleteExpired();
}
