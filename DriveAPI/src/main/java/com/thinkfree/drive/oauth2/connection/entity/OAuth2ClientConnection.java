package com.thinkfree.drive.oauth2.connection.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * tb_oauth2_client_connection — OAuth2 클라이언트별 사용자 연결 상태.
 *
 * 행 존재 자체가 "연결됨"이고 해제는 행 삭제다 (status 플래그 없음 — 재연결 시 재생성).
 * RS 필터(McpBearerTokenAuthenticationFilter)가 매 요청 (user_seq, client_id) 존재를 확인해
 * 해제 즉시 기발급 access 토큰까지 차단한다 (MCP_OAUTH_CONNECTIONS_HANDOFF.md).
 *
 * JPA 매핑(§1 v2 "JPA 우선"): 존재 확인·last_used_date 갱신·해제는 쓰기 리포가 준 관리 엔티티로(갱신은 {@link #changeLastUsedDate} → 커밋 때 변경 감지, 해제는 delete — W2 10-12),
 * 유일한 INSERT 가 (user_seq, client_id) upsert 라 그것만 JdbcClient({@code DefaultOAuth2ClientConnectionRepository}).
 * 날짜: upsert 는 register_date·token_issued_date 를 같은 현재 시각으로 바인딩, last_used_date 는 도메인 시각(감사 대상 아님).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_oauth2_client_connection")
public class OAuth2ClientConnection extends BaseRegisterTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long connectionSeq;
    private Integer userSeq;
    private String clientId;
    private String scopes;
    private Instant tokenIssuedDate;
    private Instant lastUsedDate;

    /** 마지막 사용일 기록 — 관리 엔티티에서 호출하면 커밋 때 이 컬럼만 UPDATE({@code @DynamicUpdate}) */
    public void changeLastUsedDate(Instant lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }
}
