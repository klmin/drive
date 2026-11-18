package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
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
 * W8 잠금 전환 확인 — 버전 잠금을 엔티티 조회 + detach 에서 생성자 식(값) + {@code @Lock(PESSIMISTIC_WRITE)} 로 바꾼 뒤에도 행 잠금이 실제로 걸리는지.
 * 트랜잭션 A 가 잠금을 쥔 채로 다른 스레드(별도 커넥션, 자동 커밋)가 같은 행을 {@code FOR UPDATE NOWAIT} 로 잡으면 실패하고, 커밋 뒤에는 성공해야 한다.
 * 커밋형이라 RepositoryTest 가 아니고, 만든 리소스·버전 행은 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("버전 행 잠금 — @Lock 생성자 식이 다른 커넥션의 FOR UPDATE NOWAIT 를 막는다")
class ResourceVersionRowLockTest {

    private static final int ADMIN = 2;
    private static final String NOWAIT = "SELECT resource_version_seq FROM tb_resource_version WHERE user_seq = ? AND resource_seq = ? FOR UPDATE NOWAIT";

    @Autowired
    private ResourceVersionRepository versionRepository;
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
            jdbc.update("DELETE FROM tb_resource_version WHERE user_seq = ? AND resource_seq = ?", ADMIN, resourceSeq);
            jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", ADMIN, resourceSeq);
        }
    }

    private void commitFileWithVersion() {
        transactionTemplate.executeWithoutResult(s -> {
            Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                    .resourceName("lock-ver.txt").originalFileName("lock-ver.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(1L)
                    .versionRegisterDate(Instant.now()).registerUserSeq(ADMIN).build();
            resourceRepository.save(r);
            r.initResourceSaveName();
            resourceRepository.flush();
            resourceSeq = r.getResourceSeq();
            ResourceVersion v = ResourceVersion.builder().userSeq(ADMIN).resourceSeq(resourceSeq).ownerSeq(ADMIN).resourceSaveName(r.getResourceSaveName())
                    .versionNumber(1).sizeByte(1L).versionRegisterDate(Instant.now()).registerUserSeq(ADMIN).build();
            versionRepository.save(v);
            v.initResourceSaveName();
        });
    }

    private void assertLockedElsewhere() {
        CompletableFuture<List<?>> other = CompletableFuture.supplyAsync(() -> jdbc.queryForList(NOWAIT, ADMIN, resourceSeq));
        assertThatThrownBy(() -> other.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("lockAllVersionsByResource(생성자 식) — 잠금을 쥔 동안 NOWAIT 실패, 커밋 뒤 성공(실패가 문장 오류가 아니라 잠금 때문)")
    void lockAllVersionsByResource_locksRows() {
        commitFileWithVersion();
        transactionTemplate.executeWithoutResult(s -> {
            assertThat(versionRepository.lockAllVersionsByResource(ADMIN, resourceSeq)).hasSize(1);
            assertLockedElsewhere();
        });
        assertThat(jdbc.queryForList(NOWAIT, ADMIN, resourceSeq)).as("커밋 뒤 해제").hasSize(1);
    }
}
