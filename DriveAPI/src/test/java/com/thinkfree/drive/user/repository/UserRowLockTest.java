package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.user.quota.alert.state.repository.UserQuotaAlertStateRepository;
import com.thinkfree.drive.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W7 잠금 전환 확인 — JdbcClient FOR UPDATE / em.find 잠금을 {@code @Lock(PESSIMISTIC_WRITE)} JPQL 로 바꾼 뒤에도 행 잠금이 실제로 걸리는지.
 * 트랜잭션 A 가 잠금을 쥔 채로 다른 스레드(별도 커넥션, 자동 커밋)가 같은 행을 {@code FOR UPDATE NOWAIT} 로 잡으면 실패해야 한다.
 * 스칼라 투영 잠금(용량 알림 상태)은 Hibernate 가 FOR UPDATE 를 붙이는지 여기서 확인한다. 커밋형이라 RepositoryTest 가 아니고, 만든 행은 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("행 잠금 — @Lock JPQL(엔티티·스칼라)이 다른 커넥션의 FOR UPDATE NOWAIT 를 막는다")
class UserRowLockTest {

    private static final int ADMIN = 2;
    private static final int ALERT_USER = 930001;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserQuotaAlertStateRepository quotaAlertStateRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_user_quota_alert_state WHERE user_seq = ?", ALERT_USER);
    }

    /** 다른 스레드(트랜잭션 밖 — 별도 커넥션)에서 NOWAIT 잠금 시도 */
    private void assertLockedElsewhere(String sql, Object... args) {
        CompletableFuture<List<?>> other = CompletableFuture.supplyAsync(() -> jdbc.queryForList(sql, args));
        assertThatThrownBy(() -> other.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("lockUser·lockUsers — 잠금을 쥔 동안 다른 커넥션의 NOWAIT 잠금 실패")
    void userLocks() {
        transactionTemplate.executeWithoutResult(s -> {
            User locked = userRepository.lockUser(ADMIN);
            assertThat(locked).isNotNull();
            assertLockedElsewhere("SELECT user_seq FROM tb_user WHERE user_seq = ? FOR UPDATE NOWAIT", ADMIN);
        });
        transactionTemplate.executeWithoutResult(s -> {
            assertThat(userRepository.lockUsers(List.of(ADMIN))).hasSize(1);
            assertLockedElsewhere("SELECT user_seq FROM tb_user WHERE user_seq = ? FOR UPDATE NOWAIT", ADMIN);
        });
        assertThat(jdbc.queryForList("SELECT user_seq FROM tb_user WHERE user_seq = ? FOR UPDATE NOWAIT", ADMIN)).as("커밋 뒤 해제").hasSize(1);
    }

    @Test
    @DisplayName("lockLastThresholdPercentByUserSeq(스칼라 투영) — FOR UPDATE 가 붙어 다른 커넥션의 NOWAIT 잠금 실패, 값은 DB 값")
    void quotaAlertStateScalarLock() {
        jdbc.update("INSERT INTO tb_user_quota_alert_state (user_seq, last_threshold_percent, register_date) VALUES (?, 70, now())", ALERT_USER);
        transactionTemplate.executeWithoutResult(s -> {
            assertThat(quotaAlertStateRepository.lockLastThresholdPercentByUserSeq(ALERT_USER)).isEqualTo(70);
            assertLockedElsewhere("SELECT user_seq FROM tb_user_quota_alert_state WHERE user_seq = ? FOR UPDATE NOWAIT", ALERT_USER);
        });
        assertThat(jdbc.queryForList("SELECT user_seq FROM tb_user_quota_alert_state WHERE user_seq = ? FOR UPDATE NOWAIT", ALERT_USER))
                .as("커밋 뒤 해제 — 위 실패가 문장 오류가 아니라 잠금 때문").hasSize(1);
    }
}
