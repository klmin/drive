package com.thinkfree.drive.oauth2.connection.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 연결 목록 조회 결과 — tb_oauth2_client_connection LEFT JOIN oauth2_registered_client.
 * clientName은 클라이언트 행이 삭제된 경우(재등록 등) null일 수 있다 — 표시 계층에서 clientId로 대체.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OAuth2ClientConnectionData {

    private Long connectionSeq;
    private String clientId;
    private String clientName;
    private String scopes;
    private Instant registerDate;
    private Instant lastUsedDate;
}
