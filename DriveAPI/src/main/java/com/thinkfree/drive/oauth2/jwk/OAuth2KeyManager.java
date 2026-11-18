package com.thinkfree.drive.oauth2.jwk;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;
import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;
import com.thinkfree.drive.oauth2.jwk.service.OAuth2JwkFindService;
import com.thinkfree.drive.oauth2.jwk.service.OAuth2JwkService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 토큰 서명용 RS256 키쌍(JWK) 관리 — DB(tb_oauth2_jwk) 영속화 (MCP_OAUTH_FLOW.md §5.3-5).
 *
 * 기존 Drive JWT(HS256, jwt.secret.key)와 신뢰 도메인을 분리하기 위해 OAuth2 전용
 * 비대칭 키쌍을 별도로 둔다. 멀티 WAS에서 같은 키를 공유해야 하므로(A가 발급한 토큰을
 * B가 검증) 파일이 아니라 DB에 저장하고, 각 인스턴스는 파싱된 키를 메모리에 캐싱한다.
 *
 * 키 상태는 expiration_date 하나로 표현:
 * - NULL       = 현역 (서명 + 검증). 항상 1개만 유지 — 서명 키가 여럿이면 인코더가 모호성 에러.
 * - 미래 시각  = rotate로 강등된 구 키. 그 시각까지 검증만 (직전 발급 access 토큰 생존 보장).
 * - 과거 시각  = 소멸 — 주기 리프레시가 삭제.
 *
 * 캐시 갱신 트리거 3개:
 * - kid 미스 폴백: 다른 WAS가 rotate한 새 키를 재기동 없이 즉시 따라잡는 경로 (쿨다운으로 DB 남용 방지)
 * - 주기 리프레시: 서명 키 교체를 느슨하게 따라잡는 경로 + 만료 키 청소
 * - rotate() 직후: 자기 캐시 즉시 반영
 *
 * 최초 기동 시 현역 키가 없으면 생성해 INSERT. 여러 인스턴스가 동시에 INSERT해도
 * 깨지지 않는다 — findAllValid의 최신 우선 정렬로 모두가 같은 키를 서명 키로 수렴하고,
 * 잉여 키는 무해한 검증 키로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2KeyManager {

    /** rotate 후 구 서명 키의 검증 유예 — RS256 서명 대상은 access 토큰(TTL 1h)뿐이므로 여유를 둔 24h면 충분 */
    private static final Duration ROTATED_KEY_GRACE = Duration.ofHours(24);
    /** kid 미스로 인한 DB 재조회 최소 간격 — 위조 kid 연타로 DB를 두들기는 것 방지 */
    private static final Duration RELOAD_COOLDOWN = Duration.ofSeconds(60);
    private static final int KEY_SIZE = 2048;

    private final OAuth2JwkService oauth2JwkService;
    private final OAuth2JwkFindService oauth2JwkFindService;

    /** 불변 스냅샷 — reload가 통째로 교체하므로 읽는 쪽은 락 없이 안전 */
    private record KeyCache(Map<String, RSAPublicKey> verificationKeys, RSAKey signingKey) {}

    private volatile KeyCache cache = new KeyCache(Map.of(), null);
    private volatile Instant lastReloadAt = Instant.EPOCH;

    @PostConstruct
    protected void init() {
        reload();
        if (cache.signingKey() == null) {
            createAndSaveSigningKey();
            reload();
        }
        if (cache.signingKey() == null) {
            // 키 없이 기동하면 서명·검증 경로가 조용히 죽으므로 fail-fast
            throw new IllegalStateException("OAuth2 서명 키 초기화 실패 (tb_oauth2_jwk)");
        }
        log.info("[OAuth2KeyManager] 키 캐시 초기화 완료. signingKid={} verificationKeys={}",
                cache.signingKey().getKeyID(), cache.verificationKeys().size());
    }

    /** SAS 서명용 — 현역 키 1개만 담아 반환 (JWKSource 빈이 발급 때마다 호출). */
    public JWKSet signingJwkSet() {
        RSAKey signingKey = cache.signingKey();
        if (signingKey == null) {
            throw new IllegalStateException("OAuth2 서명 키 없음");
        }
        return new JWKSet(signingKey);
    }

    /**
     * 검증용 공개키 조회 — 토큰 헤더의 kid로 선택 (McpBearerTokenAuthenticationFilter 등 RS가 호출).
     * 캐시 미스면 다른 WAS가 rotate했을 수 있으므로 1회 재조회 후 재시도. 없으면 null(=401).
     */
    public RSAPublicKey verificationKey(String kid) {
        RSAPublicKey key = cache.verificationKeys().get(kid);
        if (key == null) {
            reloadIfCooldownPassed();
            key = cache.verificationKeys().get(kid);
        }
        return key;
    }

    /**
     * 키 회전 — 현역 키를 검증 전용으로 강등하고 새 현역 키를 INSERT.
     * 다음 발급부터 새 키로 서명되고, 구 키 토큰은 유예기간 동안 계속 검증된다.
     * 다른 WAS는 주기 리프레시/kid 미스 폴백으로 따라온다. (추후 어드민 API에서 호출)
     *
     * 트랜잭션(강등+저장 원자성)은 별도 빈인 OAuth2JwkService#rotateSigningKey의 경계 —
     * 커밋이 그 메서드 리턴 시점, 즉 이 락 안에서 끝난다. rotate() 자체에 @Transactional을
     * 붙이면 안 된다: 프록시가 락 바깥을 감싸 "락 해제 후 커밋"이 되고, 그 틈의 reload가
     * 커밋 전 스냅샷으로 캐시를 되돌릴 수 있다. 락이 커밋까지 감싸고 reload는 커밋 후에.
     */
    public synchronized String rotate() {
        RSAKey newKey = generateKey();
        oauth2JwkService.rotateSigningKey(Instant.now().plus(ROTATED_KEY_GRACE), OAuth2Jwk.of(newKey));
        reload(); // 커밋 후 — 실패한 회전이 캐시에 실리는 일이 없다
        log.info("[OAuth2KeyManager] 키 회전 완료. newSigningKid={}", newKey.getKeyID());
        return newKey.getKeyID();
    }

    /**
     * 주기 리프레시 — 다른 WAS의 rotate 반영 + 만료 키 청소.
     * 인스턴스별 자기 캐시 갱신이므로 ShedLock을 걸지 않는다 (모든 인스턴스가 실행돼야 함).
     */
    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    protected void refresh() {
        try {
            int deleted = oauth2JwkService.deleteExpired();
            if (deleted > 0) {
                log.info("[OAuth2KeyManager] 만료 키 {}건 삭제", deleted);
            }
            reload();
        } catch (DataAccessException e) {
            // 캐시는 기존 스냅샷 유지 — 다음 주기에 재시도
            log.warn("[OAuth2KeyManager] 주기 리프레시 실패", e);
        }
    }

    public synchronized void reload() {
        List<OAuth2JwkData> rows = oauth2JwkFindService.findAllValid(); // 최신 우선 정렬
        Map<String, RSAPublicKey> verificationKeys = new HashMap<>();
        RSAKey signingKey = null;

        for (OAuth2JwkData row : rows) {
            try {
                RSAKey key = RSAKey.parse(row.getJwkJson());
                verificationKeys.put(key.getKeyID(), key.toRSAPublicKey());
                if (signingKey == null && row.isSigning()) {
                    signingKey = key; // 정렬 최신 우선 — 현역이 복수여도 모든 인스턴스가 같은 키로 수렴
                }
            } catch (ParseException | JOSEException e) {
                log.error("[OAuth2KeyManager] JWK 파싱 실패 — 해당 키 무시. kid={}", row.getKid(), e);
            }
        }

        cache = new KeyCache(Map.copyOf(verificationKeys), signingKey);
        lastReloadAt = Instant.now();
    }

    /** 락 안에서 쿨다운을 재확인 — kid 미스가 동시다발이어도 reload는 쿨다운당 1회만 나간다. */
    private synchronized void reloadIfCooldownPassed() {
        if (Duration.between(lastReloadAt, Instant.now()).compareTo(RELOAD_COOLDOWN) < 0) {
            return;
        }
        reload();
    }

    private void createAndSaveSigningKey() {
        RSAKey newKey = generateKey();
        try {
            oauth2JwkService.save(OAuth2Jwk.of(newKey));
            log.info("[OAuth2KeyManager] 서명 키 신규 생성·저장 완료. kid={}", newKey.getKeyID());
        } catch (DataAccessException e) {
            // 동시 기동한 다른 인스턴스가 먼저 넣었을 수 있음 — 이어지는 reload가 그 키를 집는다
            log.warn("[OAuth2KeyManager] 서명 키 저장 실패 — 기존 키 재조회로 진행. kid={}", newKey.getKeyID(), e);
        }
    }

    private RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(KEY_SIZE)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("OAuth2 RS256 키쌍 생성 실패", e);
        }
    }
}
