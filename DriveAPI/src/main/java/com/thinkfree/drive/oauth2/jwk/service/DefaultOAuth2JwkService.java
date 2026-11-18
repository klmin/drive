package com.thinkfree.drive.oauth2.jwk.service;

import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;
import com.thinkfree.drive.oauth2.jwk.repository.OAuth2JwkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * tb_oauth2_jwk 영속화 서비스 — 캐시·회전 정책은 OAuth2KeyManager가 담당하고
 * 여기는 DB 반영만 맡는다.
 *
 * ⚠️ rotateSigningKey의 트랜잭션 경계는 반드시 이 별도 빈에 있어야 한다.
 * OAuth2KeyManager.rotate()는 synchronized인데, rotate() 자체에 @Transactional을 붙이면
 * 프록시가 락 바깥을 감싸 "락 해제 후 커밋"이 되고, 그 틈의 reload가 커밋 전
 * 스냅샷으로 캐시를 되돌릴 수 있다. 별도 빈의 메서드 호출이면 커밋이 메서드 리턴
 * 시점(= KeyManager의 락 안)에 끝난다. 같은 이유로 이 메서드를 KeyManager 안으로
 * 인라인하면 안 된다(self-invocation은 프록시를 안 타 트랜잭션이 사라진다).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultOAuth2JwkService implements OAuth2JwkService {

    private final OAuth2JwkRepository oauth2JwkRepository;

    @Override
    public void save(OAuth2Jwk jwk) {
        oauth2JwkRepository.save(jwk);
    }

    @Override
    public void rotateSigningKey(Instant demotedKeyExpirationDate, OAuth2Jwk newKey) {
        oauth2JwkRepository.expireSigningKeys(demotedKeyExpirationDate);
        oauth2JwkRepository.save(newKey);
    }

    @Override
    public int deleteExpired() {
        return oauth2JwkRepository.deleteExpired();
    }
}
