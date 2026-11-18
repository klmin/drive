package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W10 잠금 전환 확인 — 버전 작업 잠금을 JdbcClient FOR UPDATE(부분 엔티티)에서 {@code @Lock(PESSIMISTIC_WRITE)} 생성자 식(값)으로 바꾼 뒤에도 행 잠금이 실제로 걸리는지.
 * 트랜잭션 A 가 잠금을 쥔 채로 다른 스레드(별도 커넥션, 자동 커밋)가 같은 행을 {@code FOR UPDATE NOWAIT} 로 잡으면 실패하고, 커밋 뒤에는 성공해야 한다.
 * 커밋형이라 RepositoryTest 가 아니고, 만든 리소스 행은 끝에 지운다(ResourceVersionRowLockTest 와 같은 방식).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("리소스 행 잠금 — @Lock 생성자 식이 다른 커넥션의 FOR UPDATE NOWAIT 를 막는다")
class ResourceRowLockTest {

    private static final int ADMIN = 2;
    private static final String NOWAIT = "SELECT resource_seq FROM tb_resource WHERE user_seq = ? AND resource_seq = ? FOR UPDATE NOWAIT";

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private Long resourceSeq;

    @AfterEach
    void cleanUp() {
        if (resourceSeq != null) {
            jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", ADMIN, resourceSeq);
        }
    }

    private void commitFile() {
        transactionTemplate.executeWithoutResult(s -> {
            Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                    .resourceName("lock-res.txt").originalFileName("lock-res.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(1L)
                    .versionRegisterDate(Instant.now()).registerUserSeq(ADMIN).build();
            resourceRepository.save(r);
            resourceSeq = r.getResourceSeq();
        });
    }

    @Test
    @DisplayName("lockResourceForVersioning(생성자 식) — 잠금을 쥔 동안 NOWAIT 실패, 커밋 뒤 성공(실패가 문장 오류가 아니라 잠금 때문)")
    void lockResourceForVersioning_locksRow() {
        commitFile();
        transactionTemplate.executeWithoutResult(s -> {
            assertThat(resourceRepository.lockResourceForVersioning(resourceSeq, ADMIN)).isNotNull();
            CompletableFuture<List<?>> other = CompletableFuture.supplyAsync(() -> jdbc.queryForList(NOWAIT, ADMIN, resourceSeq));
            assertThatThrownBy(() -> other.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(DataAccessException.class);
        });
        assertThat(jdbc.queryForList(NOWAIT, ADMIN, resourceSeq)).as("커밋 뒤 해제").hasSize(1);
    }

    @Test
    @DisplayName("lockSubtree(스칼라 @Lock JPQL) — 경로 접두사 아래 미삭제 행만 잠그고, 다른 경로 행은 잠그지 않는다")
    void lockSubtree_locksOnlyPrefixedRows() {
        commitFile();
        long inside = resourceSeq;
        commitFile();
        long outside = resourceSeq;
        try {
            jdbc.update("UPDATE tb_resource SET full_path_seq = ? WHERE user_seq = ? AND resource_seq = ?", "/987654321/" + inside + "/", ADMIN, inside);
            jdbc.update("UPDATE tb_resource SET full_path_seq = ? WHERE user_seq = ? AND resource_seq = ?", "/987654322/" + outside + "/", ADMIN, outside);
            transactionTemplate.executeWithoutResult(s -> {
                assertThat(resourceRepository.lockSubtree(ADMIN, "/987654321/")).containsExactly(inside);
                CompletableFuture<List<?>> lockedRow = CompletableFuture.supplyAsync(() -> jdbc.queryForList(NOWAIT, ADMIN, inside));
                assertThatThrownBy(() -> lockedRow.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(DataAccessException.class);
                assertThat(CompletableFuture.supplyAsync(() -> jdbc.queryForList(NOWAIT, ADMIN, outside)).join()).as("다른 경로는 안 잠김").hasSize(1);
            });
            assertThat(jdbc.queryForList(NOWAIT, ADMIN, inside)).as("커밋 뒤 해제").hasSize(1);
        } finally {
            jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", ADMIN, inside);
        }
    }
}
