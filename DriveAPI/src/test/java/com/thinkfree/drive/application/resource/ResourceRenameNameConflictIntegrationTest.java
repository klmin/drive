package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.api.v1.file.controller.FileController;
import com.thinkfree.drive.api.v1.file.request.FileRenameRequest;
import com.thinkfree.drive.api.v1.folder.controller.FolderController;
import com.thinkfree.drive.api.v1.folder.request.FolderRenameRequest;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 이름 변경 중복 검사 위치 — 잠근 행의 실제 부모 기준이어야 한다.
 * 실제 커밋이 필요해 Testcontainers 에 커밋하고 끝에 지운다(사용자 2, 테넌트 1 픽스처).
 */
@SpringBootTest
@ActiveProfiles("test")
class ResourceRenameNameConflictIntegrationTest {

    static final int USER = 2;

    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceRetryService resources;
    @Autowired FileController files;
    @Autowired FolderController folders;
    @MockitoBean ResourceAuditService audit;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean PostProcessingOrchestratorRetry postprocessing;

    final List<Long> created = new ArrayList<>();
    CustomUserDetails user;

    @BeforeEach
    void setUp() {
        user = mock(CustomUserDetails.class);
        when(user.getUserSeq()).thenReturn(USER);
        when(user.getUsername()).thenReturn("fixture");
        TenantContext.set(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void cleanUp() {
        for (long id : created) {
            jdbc.update("DELETE FROM tb_resource_version WHERE user_seq=? AND resource_seq=?", USER, id);
            jdbc.update("DELETE FROM tb_resource WHERE user_seq=? AND resource_seq=?", USER, id);
        }
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    long folder(Long parent) {
        // 이름은 originalFolderName 기준 복사 번호로 정해지므로 폴더마다 고유하게 준다
        String name = "nc-" + UUID.randomUUID();
        long id = resources.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(USER).ownerSeq(USER).parentFolderSeq(parent)
                .baseFolderName(name).originalFolderName(name).build()).resourceSeq();
        created.add(0, id);
        return id;
    }

    long file(Long parent) {
        String name = "nc-" + UUID.randomUUID();
        String path = parent == null ? "/" : jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq=? AND user_seq=?", String.class, parent, USER);
        long id = jdbc.queryForObject("""
                INSERT INTO tb_resource(user_seq,owner_seq,latest_owner_seq,resource_type,resource_name,original_file_name,resource_save_name,ext,content_type,
                                        size_byte,register_user_seq,parent_folder_seq,full_path_seq)
                VALUES(?,?,?,'FILE',?,?,?,'txt','text/plain',10,?,?,?) RETURNING resource_seq""",
                Long.class, USER, USER, USER, name + ".txt", name, "rename-conflict-test/" + name, USER, parent, path);
        created.add(0, id);
        return id;
    }

    String nameOf(long id) {
        return jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, id);
    }

    static ErrorCode code(Runnable r) {
        try {
            r.run();
            return null;
        } catch (ApiRuntimeException e) {
            return e.getCode();
        }
    }

    @Test
    @DisplayName("하위 폴더에서 형제 폴더와 같은 이름으로 바꾸면 중복으로 거부된다")
    void folderRenameToSiblingNameInSubfolderIsRejected() {
        long parent = folder(null);
        long sibling = folder(parent);
        long target = folder(parent);
        String before = nameOf(target);

        assertThat(code(() -> folders.rename(target, new FolderRenameRequest(null, nameOf(sibling)), user)))
                .isEqualTo(ResourceErrorCode.CONFLICT);
        assertThat(nameOf(target)).isEqualTo(before);
    }

    @Test
    @DisplayName("하위 폴더에서 루트에만 있는 이름으로 바꾸는 것은 중복이 아니다")
    void folderRenameToRootOnlyNameInSubfolderSucceeds() {
        long rootFolder = folder(null);
        long parent = folder(null);
        long target = folder(parent);
        String rootName = nameOf(rootFolder);

        folders.rename(target, new FolderRenameRequest(null, rootName), user);

        assertThat(nameOf(target)).isEqualTo(rootName);
    }

    @Test
    @DisplayName("하위 폴더에서 파일을 형제 파일과 같은 이름으로 바꾸면 중복으로 거부된다")
    void fileRenameToSiblingNameInSubfolderIsRejected() {
        long parent = folder(null);
        long sibling = file(parent);
        long target = file(parent);
        String before = nameOf(target);

        assertThat(code(() -> files.rename(target, new FileRenameRequest(null, nameOf(sibling), NameConflictPolicy.NONE), user)))
                .isEqualTo(ResourceErrorCode.CONFLICT);
        assertThat(nameOf(target)).isEqualTo(before);
    }

    @Test
    @DisplayName("기존 이름 그대로 제출하면 자기 자신과는 중복이 아니다 — 하위 폴더·루트 폴더·파일")
    void renameToOwnNameIsNotConflict() {
        long parent = folder(null);
        long subFolder = folder(parent);
        long rootFolder = folder(null);
        long file = file(parent);
        String subName = nameOf(subFolder);
        String rootName = nameOf(rootFolder);
        String fileName = nameOf(file);

        assertThat(code(() -> folders.rename(subFolder, new FolderRenameRequest(null, subName), user))).isNull();
        assertThat(code(() -> folders.rename(rootFolder, new FolderRenameRequest(null, rootName), user))).isNull();
        assertThat(code(() -> files.rename(file, new FileRenameRequest(null, fileName, NameConflictPolicy.NONE), user))).isNull();

        assertThat(nameOf(subFolder)).isEqualTo(subName);
        assertThat(nameOf(rootFolder)).isEqualTo(rootName);
        assertThat(nameOf(file)).isEqualTo(fileName);
    }

    @Test
    @DisplayName("폴더를 기존 이름 그대로 제출하면 하위 항목의 수정 시각을 바꾸지 않는다")
    void folderRenameToOwnNameDoesNotTouchChildren() {
        long target = folder(null);
        long childFolder = folder(target);
        long childFile = file(childFolder);
        jdbc.update("UPDATE tb_resource SET update_date = TIMESTAMP '2000-01-01 00:00:00' WHERE user_seq=? AND resource_seq IN (?, ?)", USER, childFolder, childFile);

        folders.rename(target, new FolderRenameRequest(null, nameOf(target)), user);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_resource WHERE user_seq=? AND resource_seq IN (?, ?) AND update_date = TIMESTAMP '2000-01-01 00:00:00'",
                Integer.class, USER, childFolder, childFile)).isEqualTo(2);
    }
}
