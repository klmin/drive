package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.api.v1.folder.controller.FolderController;
import com.thinkfree.drive.api.v1.folder.request.FolderRenameRequest;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.resource.service.ResourceService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 이름을 정하는 경로의 직렬화(docs/design/upload-session-redesign.md 규칙 9, §3 #8) — 같은 위치에서 동시에 같은 이름을 정하면 이름이 겹치지 않아야 한다.
 * 같은 이름의 기존 행만 잠그던 방식은 그런 행이 없으면 아무것도 잠그지 못해, 동시 요청이 둘 다 같은 이름을 얻었다.
 * 지금은 부모 폴더 행(루트면 사용자 행)을 잠근다. 실제 커밋이 필요해 Testcontainers 에 커밋하고 끝에 지운다(사용자 2, 테넌트 1 픽스처).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("이름 결정 경로 잠금 — 같은 위치 동시 생성·이름 변경·이동의 이름 중복 방지")
class ResourceNamingLockConcurrencyIntegrationTest {

    static final int USER = 2;
    static final int THREADS = 6;

    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceRetryService resources;
    @Autowired ResourceService resourceService;
    @Autowired FolderController folders;
    @MockitoBean ResourceAuditService audit;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean PostProcessingOrchestratorRetry postprocessing;

    final List<Long> created = Collections.synchronizedList(new ArrayList<>());
    CustomUserDetails user;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        user = mock(CustomUserDetails.class);
        when(user.getUserSeq()).thenReturn(USER);
        when(user.getUsername()).thenReturn("fixture");
        login();
        pool = Executors.newFixedThreadPool(THREADS);
    }

    void login() {
        TenantContext.set(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        // 하위부터 지운다 — 만든 순서의 역순
        List<Long> ids = new ArrayList<>(created);
        Collections.reverse(ids);
        for (long id : ids) {
            jdbc.update("DELETE FROM tb_resource WHERE user_seq=? AND resource_seq=?", USER, id);
        }
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    long folder(Long parent, String name) {
        long id = resources.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(USER).ownerSeq(USER).parentFolderSeq(parent)
                .baseFolderName(name).originalFolderName(name).build()).resourceSeq();
        created.add(id);
        return id;
    }

    long file(Long parent, String name) {
        String path = parent == null ? "/" : jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq=? AND user_seq=?", String.class, parent, USER);
        long id = jdbc.queryForObject("""
                INSERT INTO tb_resource(user_seq,owner_seq,latest_owner_seq,resource_type,resource_name,original_file_name,resource_save_name,ext,content_type,
                                        size_byte,register_user_seq,parent_folder_seq,full_path_seq)
                VALUES(?,?,?,'FILE',?,?,?,'txt','text/plain',10,?,?,?) RETURNING resource_seq""",
                Long.class, USER, USER, USER, name + ".txt", name, "naming-lock-test/" + UUID.randomUUID(), USER, parent, path);
        created.add(id);
        return id;
    }

    List<String> namesIn(long parent) {
        return jdbc.queryForList("SELECT resource_name FROM tb_resource WHERE user_seq=? AND parent_folder_seq=? AND is_delete='N'", String.class, USER, parent);
    }

    /** 모두 같은 순간에 출발시키고 결과(예외면 오류 코드, 성공이면 null)를 모은다 */
    List<ErrorCode> race(List<Callable<?>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                login();
                start.await();
                try {
                    task.call();
                    return null;
                } catch (ApiRuntimeException e) {
                    return e.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                }
            }));
        }
        start.countDown();
        List<ErrorCode> results = new ArrayList<>();
        for (Future<ErrorCode> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        return results;
    }

    @Test
    @DisplayName("같은 폴더에 같은 이름의 폴더 동시 생성 — 모두 성공하고 이름은 넘버링으로 모두 다르다")
    void concurrentFolderCreateSameName() throws Exception {
        long parent = folder(null, "nl-" + UUID.randomUUID());
        String name = "same-" + UUID.randomUUID();

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> folder(parent, name));
        }
        assertThat(race(tasks)).containsOnlyNulls();

        assertThat(namesIn(parent)).hasSize(THREADS).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("루트에 같은 이름의 폴더 동시 생성 — 사용자 행 잠금으로 이름이 모두 다르다")
    void concurrentRootFolderCreateSameName() throws Exception {
        String name = "root-same-" + UUID.randomUUID();

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> folder(null, name));
        }
        assertThat(race(tasks)).containsOnlyNulls();

        List<String> names = jdbc.queryForList("SELECT resource_name FROM tb_resource WHERE user_seq=? AND parent_folder_seq IS NULL AND resource_name LIKE ?",
                String.class, USER, "%" + name + "%");
        assertThat(names).hasSize(THREADS).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("같은 폴더의 서로 다른 폴더들을 동시에 같은 이름으로 변경(중복 거부 정책) — 하나만 성공, 나머지는 CONFLICT, 이름은 겹치지 않는다")
    void concurrentRenameToSameName() throws Exception {
        long parent = folder(null, "nl-" + UUID.randomUUID());
        List<Long> targets = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            targets.add(folder(parent, "r-" + UUID.randomUUID()));
        }
        String newName = "renamed-" + UUID.randomUUID();

        List<Callable<?>> tasks = new ArrayList<>();
        for (long target : targets) {
            tasks.add(() -> folders.rename(target, new FolderRenameRequest(null, newName), user));
        }
        List<ErrorCode> results = race(tasks);

        assertThat(results).filteredOn(r -> r == null).hasSize(1);
        assertThat(results).filteredOn(r -> r != null).allMatch(r -> r == ResourceErrorCode.CONFLICT);
        assertThat(namesIn(parent)).doesNotHaveDuplicates().filteredOn(newName::equals).hasSize(1);
    }

    @Test
    @DisplayName("다른 폴더의 같은 이름 파일들을 한 폴더로 동시 이동 — 모두 성공하고 이름은 넘버링으로 모두 다르다")
    void concurrentMoveSameNameIntoFolder() throws Exception {
        long destination = folder(null, "nl-dest-" + UUID.randomUUID());
        String name = "mv-" + UUID.randomUUID();
        List<Long> files = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long source = folder(null, "nl-src-" + UUID.randomUUID());
            files.add(file(source, name));
        }

        List<Callable<?>> tasks = new ArrayList<>();
        for (long f : files) {
            tasks.add(() -> resourceService.fileMove(ResourceMoveDto.builder().resourceSeq(f).parentFolderSeq(destination).userSeq(USER)
                    .resourceType(ResourceType.FILE).isDelete(false).build()));
        }
        assertThat(race(tasks)).containsOnlyNulls();

        assertThat(namesIn(destination)).hasSize(THREADS).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("부모 폴더와 그 자식 폴더 동시 이름 변경(서로 다른 이름) — 잠금이 위에서 아래로라 교착 없이 둘 다 성공(Codex 리뷰 P1 회귀)")
    void concurrentParentAndChildRenameDoNotDeadlock() throws Exception {
        for (int round = 0; round < 5; round++) {
            long grand = folder(null, "nl-g-" + UUID.randomUUID());
            long parent = folder(grand, "nl-p-" + UUID.randomUUID());
            long child = folder(parent, "nl-c-" + UUID.randomUUID());
            long grandChild = folder(child, "nl-gc-" + UUID.randomUUID());
            String parentName = "p-renamed-" + UUID.randomUUID();
            String childName = "c-renamed-" + UUID.randomUUID();

            List<Callable<?>> tasks = List.of(
                    () -> folders.rename(parent, new FolderRenameRequest(null, parentName), user),
                    () -> folders.rename(child, new FolderRenameRequest(null, childName), user));
            assertThat(race(tasks)).as("round %d", round).containsOnlyNulls();

            assertThat(jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, parent)).isEqualTo(parentName);
            assertThat(jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, child)).isEqualTo(childName);
            // 하위 경로가 두 변경을 모두 반영했는지 — 손자 폴더 경로에 새 부모·자식 이름이 둘 다 있어야 한다
            String grandChildPath = jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, grandChild);
            assertThat(grandChildPath).contains(parentName).contains(childName);
        }
    }

    @Test
    @DisplayName("루트 폴더 이름 변경과 그 자식 폴더의 루트 이동 동시 — 소유자 행을 먼저 잡아 교착 없이 둘 다 반영(Codex 리뷰 P1 회귀)")
    void concurrentRootRenameAndChildMoveToRootDoNotDeadlock() throws Exception {
        for (int round = 0; round < 5; round++) {
            long parent = folder(null, "nl-rp-" + UUID.randomUUID());
            long child = folder(parent, "nl-rc-" + UUID.randomUUID());
            folder(child, "nl-rgc-" + UUID.randomUUID());
            String parentName = "rp-renamed-" + UUID.randomUUID();

            List<Callable<?>> tasks = List.of(
                    () -> folders.rename(parent, new FolderRenameRequest(null, parentName), user),
                    () -> resourceService.folderMove(ResourceMoveDto.builder().resourceSeq(child).parentFolderSeq(null).userSeq(USER)
                            .resourceType(ResourceType.FOLDER).isDelete(false).build()));
            assertThat(race(tasks)).as("round %d", round).containsOnlyNulls();

            assertThat(jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, parent)).isEqualTo(parentName);
            assertThat(jdbc.queryForObject("SELECT parent_folder_seq FROM tb_resource WHERE user_seq=? AND resource_seq=?", Long.class, USER, child)).isNull();
            assertThat(jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, child))
                    .as("루트로 옮긴 폴더 경로에 옛 부모 이름이 남지 않는다").doesNotContain(parentName);
        }
    }
}
