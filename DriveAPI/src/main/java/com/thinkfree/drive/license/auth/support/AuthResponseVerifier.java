package com.thinkfree.drive.license.auth.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.license.auth.client.LicenseAuthClientProvider;
import com.thinkfree.drive.license.auth.result.SignedLicenseInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증 서버 응답의 signedData 를 검증하고 원장 정보를 꺼낸다.
 *
 * 공개키는 응답의 serverKid 로 고른다. 인증 서버가 키를 교체하면 모르는 kid 가 오므로
 * 그때 공개키 목록을 다시 받는다. 주소별로 따로 캐시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthResponseVerifier {

    private final LicenseAuthClientProvider clientProvider;
    private final Map<String, Map<String, RSAKey>> keysByBaseUrl = new ConcurrentHashMap<>();

    /**
     * @throws IllegalStateException 서명이 맞지 않거나 공개키를 찾지 못한 경우
     */
    public SignedLicenseInfo verify(String baseUrl, String signedData, String serverKid) {
        if (serverKid == null || serverKid.isBlank()) {
            throw new IllegalStateException("serverKid 가 없는 응답");
        }

        try {
            SignedJWT jwt = SignedJWT.parse(signedData);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IllegalStateException("지원하지 않는 서명 알고리즘: " + jwt.getHeader().getAlgorithm());
            }
            if (!jwt.verify(new RSASSAVerifier(findKey(baseUrl, serverKid)))) {
                throw new IllegalStateException("응답 서명 불일치. kid=" + serverKid);
            }
            return SignedLicenseInfo.parse(jwt.getJWTClaimsSet().getSubject());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("응답 서명 검증 실패. kid=" + serverKid, e);
        }
    }

    private RSAKey findKey(String baseUrl, String kid) throws Exception {
        RSAKey cached = keysByBaseUrl.getOrDefault(baseUrl, Map.of()).get(kid);
        if (cached != null) {
            return cached;
        }

        Map<String, RSAKey> fetched = fetchKeys(baseUrl);
        keysByBaseUrl.put(baseUrl, fetched);

        RSAKey key = fetched.get(kid);
        if (key == null) {
            throw new IllegalStateException("공개키 목록에 없는 kid=" + kid);
        }
        return key;
    }

    private Map<String, RSAKey> fetchKeys(String baseUrl) throws Exception {
        JWKSet jwkSet = JWKSet.parse(clientProvider.get(baseUrl).jwks());
        Map<String, RSAKey> keys = new ConcurrentHashMap<>();
        for (JWK jwk : jwkSet.getKeys()) {
            if (jwk instanceof RSAKey rsaKey && jwk.getKeyID() != null) {
                keys.put(jwk.getKeyID(), rsaKey);
            }
        }
        log.info("[ AuthResponseVerifier ] 인증 서버 공개키를 받았다. baseUrl={}, kids={}", baseUrl, keys.keySet());
        return keys;
    }
}
