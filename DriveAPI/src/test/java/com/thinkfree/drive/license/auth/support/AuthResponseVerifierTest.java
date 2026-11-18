package com.thinkfree.drive.license.auth.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.license.auth.client.LicenseAuthClient;
import com.thinkfree.drive.license.auth.client.LicenseAuthClientProvider;
import com.thinkfree.drive.license.auth.result.SignedLicenseInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 서버 응답 서명 검증")
class AuthResponseVerifierTest {

    private static final String BASE_URL = "https://auth.example/api/public/v1";
    private static final String SUBJECT = "licenseId:100|startDate:20261001|endDate:20271001|addFlag:seat::200,auth::" + BASE_URL + "|licenseType:SEAT";

    private static KeyPair serverKey;
    private static KeyPair otherKey;

    @Mock LicenseAuthClientProvider clientProvider;
    @Mock LicenseAuthClient client;

    private AuthResponseVerifier verifier;

    @BeforeAll
    static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        serverKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        verifier = new AuthResponseVerifier(clientProvider);
        given(clientProvider.get(BASE_URL)).willReturn(client);
    }

    private static String jwks(String kid, KeyPair keyPair) {
        return new JWKSet(new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).keyID(kid).build()).toString();
    }

    private static String sign(KeyPair keyPair, String subject) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().subject(subject).build());
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    @Test
    @DisplayName("serverKid 의 공개키로 서명이 맞으면 원장 정보를 꺼낸다")
    void validSignature_parsesSubject() throws Exception {
        given(client.jwks()).willReturn(jwks("k1", serverKey));

        SignedLicenseInfo info = verifier.verify(BASE_URL, sign(serverKey, SUBJECT), "k1");

        assertThat(info.licenseId()).isEqualTo(100L);
        assertThat(info.startDate()).isEqualTo("20261001");
        assertThat(info.endDate()).isEqualTo("20271001");
        assertThat(info.addFlag()).isEqualTo("seat::200,auth::" + BASE_URL);
        assertThat(info.licenseType()).isEqualTo("SEAT");
    }

    @Test
    @DisplayName("다른 키로 서명된 응답 → 거부")
    void forgedSignature_rejected() throws Exception {
        given(client.jwks()).willReturn(jwks("k1", serverKey));

        assertThatThrownBy(() -> verifier.verify(BASE_URL, sign(otherKey, SUBJECT), "k1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("모르는 kid → 공개키 목록을 다시 받아 찾는다 (키 교체 대응)")
    void unknownKid_refetches() throws Exception {
        given(client.jwks()).willReturn(jwks("k1", serverKey), jwks("k2", otherKey));
        verifier.verify(BASE_URL, sign(serverKey, SUBJECT), "k1");

        SignedLicenseInfo info = verifier.verify(BASE_URL, sign(otherKey, SUBJECT), "k2");

        assertThat(info.licenseId()).isEqualTo(100L);
        then(client).should(times(2)).jwks();
    }

    @Test
    @DisplayName("공개키 목록에 없는 kid → 거부")
    void kidNotInJwks_rejected() throws Exception {
        given(client.jwks()).willReturn(jwks("k1", serverKey));

        assertThatThrownBy(() -> verifier.verify(BASE_URL, sign(serverKey, SUBJECT), "unknown"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("라이선스를 찾지 못한 응답(빈 subject) → null")
    void emptySubject_returnsNull() throws Exception {
        given(client.jwks()).willReturn(jwks("k1", serverKey));

        assertThat(verifier.verify(BASE_URL, sign(serverKey, ""), "k1")).isNull();
    }
}
