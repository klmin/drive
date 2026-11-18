package com.thinkfree.drive.oauth2.service;

import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.oauth2.exception.InvalidClientRegistrationException;
import com.thinkfree.drive.oauth2.properties.OAuth2TokenProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * DCR 등록 정책의 소유자 — 어떤 클라이언트로 만들어 저장할지는 전부 여기서 결정한다.
 *
 * 등록 정책: confidential client(등록마다 동적 발급 secret, client_secret_post/basic) + PKCE 필수.
 * public client(none)로 등록하면 SAS가 refresh token을 발급하지 않아(공개 클라이언트 미지원)
 * access 만료 시마다 재로그인이 필요해진다 → RFC 7591 응답에 client_secret을 실어주면
 * 클라이언트가 저장해 token 요청에 사용한다. 등록 인스턴스별 개별 secret이라
 * 유출 영향 범위도 해당 등록 하나로 국한된다.
 *
 * secret은 BCrypt 해싱 저장(평문은 반환값에만) — SAS의 ClientSecretAuthenticationProvider가
 * 앱의 PasswordEncoder 빈(BCrypt)으로 검증하기 때문에 반드시 같은 인코더로 저장해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultOAuth2ClientRegistrationService implements OAuth2ClientRegistrationService {

    /** 스레드 세이프 — 매 등록마다 새로 만들 필요 없이 재사용 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2TokenProperty oauth2TokenProperty;

    @Override
    public OAuth2ClientRegistrationResult register(String clientName, List<String> redirectUris, List<String> scopes) {
        validateScopes(scopes);

        String clientId = UUID.randomUUID().toString();
        // 256비트 엔트로피 → Base64Url 43자 — 평문은 DCR 응답에 1회만 노출
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String clientSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String resolvedClientName = StringUtils.hasText(clientName) ? clientName : clientId;

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientIdIssuedAt(Instant.now())
                .clientName(resolvedClientName)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)              // PKCE 필수
                        // 익명 DCR + 무화면 브리지 조합에서 동의 화면이 유일한 사용자 확인 지점 —
                        // 끄면 로그인된 피해자가 공격자 클라이언트의 authorize 링크를 열기만 해도
                        // 클릭 0회로 코드가 공격자 redirect_uri로 발급된다.
                        // 이 설정은 클라이언트 행에 저장되므로 기존 등록 클라이언트에는 소급되지 않는다.
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(oauth2TokenProperty.accessTokenTtl())
                        .refreshTokenTimeToLive(oauth2TokenProperty.refreshTokenTtl())
                        .reuseRefreshTokens(false)           // refresh rotation
                        .build());
        redirectUris.forEach(builder::redirectUri);
        // SAS 동의는 scope 단위 — scope 없는 클라이언트는 동의 화면이 정상 동작하지 않을 수
        // 있으므로 미지정 등록에는 지원 scope 전체를 부여한다 (OAuth2Scope 주석 참조)
        List<String> resolvedScopes = scopes.isEmpty() ? OAuth2Scope.supportedValues() : scopes;
        resolvedScopes.forEach(builder::scope);

        RegisteredClient registeredClient = builder.build();
        registeredClientRepository.save(registeredClient);
        log.info("[OAuth2 DCR] client registered. client_id : {} name : {} redirect_uris : {}",
                clientId, resolvedClientName, redirectUris);

        return new OAuth2ClientRegistrationResult(registeredClient, clientSecret);
    }

    /**
     * 등록 scope 검증 — 등록 scope가 authorize 요청 scope의 상한이 되므로,
     * 지원 목록 밖의 scope는 등록 단계에서 거부해야 동의 화면·토큰까지 흘러가지 않는다.
     */
    private static void validateScopes(List<String> scopes) {
        if (!scopes.stream().allMatch(OAuth2Scope::isSupported)) {
            throw new InvalidClientRegistrationException("invalid_client_metadata",
                    "unsupported scope. supported scopes: " + String.join(" ", OAuth2Scope.supportedValues()));
        }
    }
}
