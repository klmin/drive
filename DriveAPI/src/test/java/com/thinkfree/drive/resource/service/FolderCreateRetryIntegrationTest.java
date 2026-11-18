package com.thinkfree.drive.resource.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.result.ResourceFolderCreateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 폴더 생성(ResourceRetryService.saveFolderWithCopyCheck) — 시도마다 새 트랜잭션이라 커밋까지 보려고 테스트 트랜잭션 없이 실행하고 끝에 지운다.
 * W10b 에서 경로(full_path_seq) UPDATE 가 커밋 때 변경 감지로 미뤄져 DB 오류가 재시도·INSERT_FAIL 변환 밖(500)으로 나가던 것을 고정(W10d D1).
 * full_path_seq 는 text(10-14 — 깊이 한계 제거)라 길이로는 실패하지 않는다 → 경로 UPDATE 만 막는 임시 CHECK 제약으로 그 단계의 DB 오류를 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("폴더 생성 — 깊은 경로(255자 초과) 저장, 경로 저장 단계 DB 오류는 INSERT_FAIL·행 남지 않음")
class FolderCreateRetryIntegrationTest {

    private static final int ADMIN = 2;

    @Autowired
    private ResourceRetryService resourceRetryService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long maxSeqBefore;

    @BeforeEach
    void setUp() {
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
    }

    private ResourceFolderCreateResult createFolder(Long parentFolderSeq, String name) {
        return resourceRetryService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(ADMIN).ownerSeq(ADMIN)
                .parentFolderSeq(parentFolderSeq).baseFolderName(name).originalFolderName(name).build());
    }

    private String seqPath(long resourceSeq) {
        return jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", String.class, ADMIN, resourceSeq);
    }

    @Test
    @DisplayName("부모 full_path_seq 300자 아래 자식 — 255자를 넘는 경로도 그대로 저장(text)")
    void deepPath_saved() {
        var parent = createFolder(null, "deep");
        String longPath = "1/".repeat(150);
        jdbc.update("UPDATE tb_resource SET full_path_seq = ? WHERE user_seq = ? AND resource_seq = ?", longPath, ADMIN, parent.resourceSeq());

        var child = createFolder(parent.resourceSeq(), "child");

        assertThat(seqPath(child.resourceSeq())).isEqualTo(longPath + child.resourceSeq() + "/");
        assertThat(seqPath(child.resourceSeq()).length()).isGreaterThan(255);
    }

    @Test
    @DisplayName("경로 UPDATE 단계에서 DB 오류 — ApiRuntimeException(INSERT_FAIL), 자식 행 0(INSERT 까지 롤백)")
    void pathUpdateDbError_insertFail() {
        var parent = createFolder(null, "deep");
        // 부모 경로 290자 — 자식 INSERT(부모 경로 그대로 290)는 통과, 이어지는 경로 UPDATE(+ seq + "/" ≥ 292)만 제약에 걸린다
        jdbc.update("UPDATE tb_resource SET full_path_seq = ? WHERE user_seq = ? AND resource_seq = ?", "1/".repeat(145), ADMIN, parent.resourceSeq());
        jdbc.execute("ALTER TABLE tb_resource ADD CONSTRAINT ck_test_full_path_seq_len CHECK (full_path_seq IS NULL OR char_length(full_path_seq) < 292)");
        try {
            assertThatThrownBy(() -> createFolder(parent.resourceSeq(), "child"))
                    .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(ResourceErrorCode.INSERT_FAIL));
        } finally {
            jdbc.execute("ALTER TABLE tb_resource DROP CONSTRAINT ck_test_full_path_seq_len");
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND parent_folder_seq = ?", Integer.class, ADMIN, parent.resourceSeq()))
                .as("INSERT 까지 롤백").isZero();
    }
}
