package com.thinkfree.drive.oauth2.connection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2AuthorizationService 데코레이터 — 저장은 JdbcOAuth2AuthorizationService에 전부 위임하고,
 * access 토큰이 포함된 save(= 최초 발급·refresh rotation)에서만 연결 upsert를 얹는다.
 * SAS에 "토큰 발급됨" 이벤트 API가 없어 저장 경로를 훅 지점으로 쓴다
 * (인가 코드 단계의 save는 access 토큰이 없어 자연히 스킵된다).
 *
 * upsert 실패는 전파한다 — 삼키면 토큰은 발급되는데 RS 필터의 연결 검증에서 전부 거부되는
 * 원인 불명 401이 되므로, 토큰 발급 자체를 실패시키는 쪽이 진단 가능하다.
 *
 * 토큰 저장과 연결 upsert 는 한 트랜잭션 — 따로 커밋하면 upsert 실패 시 refresh rotation 만 커밋돼
 * 클라이언트의 이전 refresh 토큰이 무효가 되고(재시도 불가), 연결 없는 토큰 행이 남는다(작업 E11).
 * JdbcOAuth2AuthorizationService 의 JdbcTemplate 은 같은 DataSource 라 이 트랜잭션에 참여한다.
 * (@Bean 메서드가 만든 빈이라 @Transactional 프록시가 적용되고, SAS 도 컨텍스트의 이 빈을 쓴다)
 */
@Slf4j
@RequiredArgsConstructor
public class ConnectionRecordingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2ClientConnectionService connectionService;

    @Transactional
    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);

        if (authorization.getAccessToken() == null) {
            return;
        }

        // principal_name은 07-27부터 userSeq 문자열 — 숫자가 아니면 구 데이터(userId)라 연결 추적 불가
        int userSeq;
        try {
            userSeq = Integer.parseInt(authorization.getPrincipalName());
        } catch (NumberFormatException e) {
            log.error("[ConnectionRecordingAuthorizationService save] non-numeric principal - {}", authorization.getPrincipalName());
            return;
        }

        RegisteredClient client = registeredClientRepository.findById(authorization.getRegisteredClientId());
        if (client == null) {
            log.error("[ConnectionRecordingAuthorizationService save] registered client not found - id : {}", authorization.getRegisteredClientId());
            return;
        }

        connectionService.recordIssuance(userSeq, client.getClientId(), authorization.getAuthorizedScopes());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
