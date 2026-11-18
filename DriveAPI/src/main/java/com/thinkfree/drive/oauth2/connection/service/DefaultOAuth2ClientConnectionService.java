package com.thinkfree.drive.oauth2.connection.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;
import com.thinkfree.drive.oauth2.connection.entity.OAuth2ClientConnection;
import com.thinkfree.drive.oauth2.connection.repository.OAuth2AuthorizationRevokeRepository;
import com.thinkfree.drive.oauth2.connection.repository.OAuth2ClientConnectionRepository;
import com.thinkfree.drive.oauth2.connection.repository.OAuth2ClientConnectionQueryRepository;
import com.thinkfree.drive.oauth2.exception.OAuth2ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 클라이언트 연결 상태 관리 (MCP_OAUTH_CONNECTIONS_HANDOFF.md).
 * 발급 훅(recordIssuance)·RS 필터(verifyAndTouch)·웹 연동 관리 API(목록·해제)가 공용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultOAuth2ClientConnectionService implements OAuth2ClientConnectionService {

    /** last_used_date 기록 스로틀 — 이 간격 안의 연속 요청은 UPDATE 생략 (쓰기 증폭 방지) */
    private static final int LAST_USED_THROTTLE_MINUTES = 5;

    private final OAuth2ClientConnectionRepository connectionRepository;
    private final OAuth2ClientConnectionQueryRepository connectionQueryRepository;
    private final OAuth2AuthorizationRevokeRepository authorizationRevokeRepository;
    private final RegisteredClientRepository registeredClientRepository;

    @Override
    public void recordIssuance(int userSeq, String clientId, Set<String> scopes) {
        connectionRepository.upsert(userSeq, clientId, String.join(" ", scopes));
    }

    /** 관리 엔티티 한 번 로드 — 오래됐으면 마지막 사용일을 고쳐 커밋 때 변경 감지(이 컬럼만 UPDATE) */
    @Override
    @Transactional
    public boolean verifyAndTouch(int userSeq, String clientId) {
        OAuth2ClientConnection connection = connectionRepository.findByUserSeqAndClientId(userSeq, clientId);
        if (connection == null) {
            return false;
        }
        touchIfStale(connection);
        return true;
    }

    private void touchIfStale(OAuth2ClientConnection connection) {
        Instant now = Instant.now();
        Instant lastUsed = connection.getLastUsedDate();
        if (lastUsed == null || lastUsed.isBefore(now.minus(LAST_USED_THROTTLE_MINUTES, ChronoUnit.MINUTES))) {
            connection.changeLastUsedDate(now.truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Override
    public List<OAuth2ClientConnectionData> findConnections(int userSeq) {
        return connectionQueryRepository.findAllWithClientNameByUserSeq(userSeq);
    }

    @Override
    @Transactional
    public void disconnect(int userSeq, long connectionSeq) {
        // PK 로드 + 소유자 판정(Java) — 남의 connectionSeq도 없는 것과 같은 CONNECTION_NOT_FOUND
        OAuth2ClientConnection connection = connectionRepository.findById(connectionSeq);
        if (connection == null || connection.getUserSeq() == null || connection.getUserSeq() != userSeq) {
            throw ApiRuntimeException.type(OAuth2ErrorCode.CONNECTION_NOT_FOUND);
        }

        connectionRepository.delete(connection);

        // 클라이언트 행이 삭제된 연결(재등록 등)은 SAS 잔재를 특정할 수 없음 — 남은 인가 행은
        // 청소 스케줄러가 만료 시 정리하고, access 차단은 connection 행 삭제만으로 이미 성립한다
        RegisteredClient client = registeredClientRepository.findByClientId(connection.getClientId());
        if (client == null) {
            log.warn("[OAuth2ClientConnection disconnect] registered client not found - clientId : {}", connection.getClientId());
            return;
        }

        String principalName = String.valueOf(userSeq);
        int authorizations = authorizationRevokeRepository.deleteAuthorizations(principalName, client.getId());
        int consents = authorizationRevokeRepository.deleteConsents(principalName, client.getId());
        log.info("[OAuth2ClientConnection disconnect] userSeq : {}, clientId : {}, authorizations : {}, consents : {}",
                userSeq, connection.getClientId(), authorizations, consents);
    }
    @Override
    @Transactional
    public void deleteByUsers(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return;
        }
        connectionRepository.deleteByUserSeqList(userSeqList);
        // SAS principal_name = userSeq 문자열(disconnect 와 같음)
        authorizationRevokeRepository.deleteAllByPrincipals(userSeqList.stream().map(String::valueOf).toList());
    }
}
