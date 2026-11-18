package com.thinkfree.drive.oauth2.connection.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.oauth2.exception.OAuth2ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2(10-12) — 연결 확인·마지막 사용일·해제가 쓰기 리포의 관리 엔티티를 한 번 로드해 변경 감지/삭제로 반영되는지, 서비스 트랜잭션 **커밋 뒤** 실제 DB 로 확인한다
 * (flush 를 부르지 않으므로 커밋 때 반영되는지가 검증 대상). 테스트 트랜잭션 없이 커밋되므로 만든 행은 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DefaultOAuth2ClientConnectionService — verifyAndTouch·disconnect 관리 엔티티 변경 감지(커밋, 실제 DB)")
class DefaultOAuth2ClientConnectionServiceTest {

    private static final int USER_SEQ = 2;
    private static final int OTHER_USER = 3;

    @Autowired
    private OAuth2ClientConnectionService service;
    @Autowired
    private JdbcTemplate jdbc;

    private String clientId;

    @BeforeEach
    void setUp() {
        clientId = "w2-client-" + UUID.randomUUID().toString().substring(0, 8);
        service.recordIssuance(USER_SEQ, clientId, Set.of("drive"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_oauth2_client_connection WHERE client_id = ?", clientId);
    }

    private Long connectionSeq() {
        return jdbc.queryForObject("SELECT connection_seq FROM tb_oauth2_client_connection WHERE user_seq = ? AND client_id = ?", Long.class, USER_SEQ, clientId);
    }

    private Instant lastUsed() {
        Timestamp ts = jdbc.queryForObject("SELECT last_used_date FROM tb_oauth2_client_connection WHERE client_id = ?", Timestamp.class, clientId);
        return ts == null ? null : ts.toInstant();
    }

    private void setLastUsed(Instant value) {
        jdbc.update("UPDATE tb_oauth2_client_connection SET last_used_date = ? WHERE client_id = ?", OffsetDateTime.ofInstant(value, ZoneOffset.UTC), clientId);
    }

    @Test
    @DisplayName("verifyAndTouch — 연결 있으면 true + 처음/5분 넘으면 커밋 때 last_used_date 기록, 5분 안이면 그대로, 없는 연결은 false")
    void verifyAndTouch() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(service.verifyAndTouch(USER_SEQ, clientId)).isTrue();
        assertThat(lastUsed()).as("처음 사용 — 변경 감지로 커밋 때 기록").isNotNull().isAfterOrEqualTo(before);

        Instant recent = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        setLastUsed(recent);
        assertThat(service.verifyAndTouch(USER_SEQ, clientId)).isTrue();
        assertThat(lastUsed()).as("5분 안 — 스로틀(UPDATE 없음)").isEqualTo(recent);

        Instant stale = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        setLastUsed(stale);
        assertThat(service.verifyAndTouch(USER_SEQ, clientId)).isTrue();
        assertThat(lastUsed()).as("5분 넘음 — 다시 기록").isAfter(stale);

        assertThat(service.verifyAndTouch(OTHER_USER, clientId)).as("다른 사용자 연결 없음").isFalse();
        assertThat(service.verifyAndTouch(USER_SEQ, clientId + "-x")).isFalse();
    }

    @Test
    @DisplayName("disconnect — 남의 연결·없는 연결은 CONNECTION_NOT_FOUND(행 유지), 본인은 커밋 때 행 삭제")
    void disconnect() {
        long seq = connectionSeq();

        assertThatThrownBy(() -> service.disconnect(OTHER_USER, seq))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(OAuth2ErrorCode.CONNECTION_NOT_FOUND));
        assertThat(connectionSeq()).as("남의 해제 시도는 무영향").isEqualTo(seq);
        assertThatThrownBy(() -> service.disconnect(USER_SEQ, 999_999_999L)).isInstanceOf(ApiRuntimeException.class);

        service.disconnect(USER_SEQ, seq);   // 등록 클라이언트 행이 없어 SAS 정리는 건너뛰는 경로(경고 로그)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_client_connection WHERE client_id = ?", Integer.class, clientId)).isZero();
    }
}
