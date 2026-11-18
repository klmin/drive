package com.thinkfree.drive.oauth2.connection.usecase;

import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;
import com.thinkfree.drive.oauth2.connection.response.OAuth2ConnectionResponse;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 연동 앱 관리 usecase — 호출자가 웹 controller 하나뿐인 웹 전용 usecase
 * (MCP_HANDOFF §2 "웹 전용 usecase 현행 유지" — Command/Info 분리 대상 아님).
 */
@Service
@RequiredArgsConstructor
public class OAuth2ConnectionUseCase {

    private final OAuth2ClientConnectionService connectionService;

    public List<OAuth2ConnectionResponse> findConnections(int userSeq) {
        return connectionService.findConnections(userSeq).stream()
                .map(this::toResponse)
                .toList();
    }

    // TODO(감사 로그): 연동 해제는 보안 이벤트로 감사 대상이나 EventName 카탈로그에
    //  OAuth 카테고리가 없어(카테고리·수집형태 신설 = 기획 협의 필요) 보류 — 확정 시 @AuditLog 부착
    public void disconnect(long connectionSeq, int userSeq) {
        connectionService.disconnect(userSeq, connectionSeq);
    }

    private OAuth2ConnectionResponse toResponse(OAuth2ClientConnectionData data) {
        // 클라이언트 등록 행이 사라진 연결(재등록 등)은 표시명이 없어 clientId로 대체
        String clientName = StringUtils.hasText(data.getClientName()) ? data.getClientName() : data.getClientId();
        List<String> scopes = StringUtils.hasText(data.getScopes())
                ? Arrays.asList(data.getScopes().split("\\s+"))
                : List.of();
        return new OAuth2ConnectionResponse(data.getConnectionSeq(), data.getClientId(), clientName,
                scopes, data.getRegisterDate(), data.getLastUsedDate());
    }
}
