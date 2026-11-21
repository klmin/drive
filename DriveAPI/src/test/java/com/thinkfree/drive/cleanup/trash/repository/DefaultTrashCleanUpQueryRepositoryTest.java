package com.thinkfree.drive.cleanup.trash.repository;

import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData;
import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData.TrashCleanUpResourceItem;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TrashCleanUp 이관 회귀 — {@link DefaultTrashCleanUpQueryRepository}(구 TrashCleanUpMapper.xml findTrashCleanUpTargets), JdbcClient.
 * 사용자 2명(trash-a 테넌트 1 / trash-b 테넌트 없음). a 의 휴지통 = 오래된 루트 폴더(하위 파일 2 — 그중 하나는 버전 2개 — 와 하위 폴더, 그리고 is_delete='N' 인 하위 1건은 제외)
 * + 최근 루트 파일(버전 없음). b 의 휴지통 = 오래된 루트 파일 1. 트랜잭션 롤백.
 * 검증 포인트 = 사용자 → 리소스 → 버전 3단 묶음(첫 등장 순, 빈 버전 목록), 11컬럼 매핑, 사용자/날짜 두 조건, isDelete 가 하위에만 걸림(null → 루트만).
 */
@DisplayName("DefaultTrashCleanUpQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultTrashCleanUpQueryRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final Timestamp OLD = Timestamp.valueOf("2026-01-01 00:00:00");
    private static final Timestamp RECENT = Timestamp.valueOf("2026-09-01 00:00:00");
    private static final Instant THRESHOLD = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private TrashCleanUpQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private int userA;
    private int userB;
    private long aRoot;
    private long aFile1;
    private long aFile2;
    private long aFolder;
    private long aRecentFile;
    private long bFile;
    private long v1;
    private long v2;
    private long v3;

    @BeforeEach
    void setUp() {
        userA = user("trash-a", "정리A", 1);
        userB = user("trash-b", "정리B", null);

        aRoot = resource(userA, "FOLDER", "a-root", null, null, "Y", null);
        aFile1 = resource(userA, "FILE", "a-file1.txt", 100L, "save/a-file1", "Y", aRoot);
        aFolder = resource(userA, "FOLDER", "a-sub", null, null, "Y", aRoot);
        aFile2 = resource(userA, "FILE", "a-file2.txt", 200L, "save/a-file2", "Y", aRoot);
        resource(userA, "FILE", "a-restored.txt", 300L, "save/a-restored", "N", aRoot);
        aRecentFile = resource(userA, "FILE", "a-recent.txt", 50L, "save/a-recent", "Y", null);
        bFile = resource(userB, "FILE", "b-file.txt", 70L, "save/b-file", "Y", null);

        v1 = version(userA, aFile1, 1, 10L, "save/a-file1/v1");
        v2 = version(userA, aFile1, 2, 20L, "save/a-file1/v2");
        v3 = version(userA, aRecentFile, 1, 5L, "save/a-recent/v1");

        trash(userA, aRoot, OLD);
        trash(userA, aRecentFile, RECENT);
        trash(userB, bFile, OLD);
    }

    // ------------------------------------------------------------------ 픽스처

    private int user(String userId, String name, Integer tenantSeq) {
        User u = User.createUser(userId, tenantSeq, null, SignupType.LOCAL, name, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u.getUserSeq();
    }

    private long resource(int owner, String type, String name, Long size, String saveName, String isDelete, Long deleteParent) {
        return jdbc.queryForObject("""
                INSERT INTO tb_resource (user_seq, owner_seq, resource_type, resource_name, size_byte, resource_save_name, is_delete, delete_parent_folder_seq, register_user_seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING resource_seq
                """, Long.class, owner, owner, type, name, size, saveName, isDelete, deleteParent, owner);
    }

    private long version(int owner, long resourceSeq, int number, long size, String saveName) {
        return jdbc.queryForObject("""
                INSERT INTO tb_resource_version (user_seq, resource_seq, resource_save_name, version_number, size_byte, status, register_user_seq)
                VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?) RETURNING resource_version_seq
                """, Long.class, owner, resourceSeq, saveName, number, size, owner);
    }

    private void trash(int owner, long resourceSeq, Timestamp registerDate) {
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq, register_date) VALUES (?, ?, ?, ?)", owner, resourceSeq, owner, registerDate);
    }

    private static TrashCleanUpTargetsData of(List<TrashCleanUpTargetsData> list, int userSeq) {
        return list.stream().filter(t -> t.getUserSeq() == userSeq).findFirst().orElseThrow();
    }

    private static TrashCleanUpResourceItem item(TrashCleanUpTargetsData data, long resourceSeq) {
        return data.getResourceTargets().stream().filter(r -> r.getResourceSeq() == resourceSeq).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ 검증

    @Test
    @DisplayName("사용자 조건 — 루트 2개와 하위(is_delete='Y')가 사용자 1묶음 안에, 버전은 리소스별로, 사용자/테넌트 컬럼 매핑")
    void byUser_groupingAndMapping() {
        List<TrashCleanUpTargetsData> list = repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, true));

        assertThat(list).hasSize(1);
        TrashCleanUpTargetsData a = list.get(0);
        assertThat(a.getUserSeq()).isEqualTo(userA);
        assertThat(a.getUserName()).isEqualTo("정리A");
        assertThat(a.getTenantSeq()).isEqualTo(1);
        assertThat(a.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq)
                .containsExactlyInAnyOrder(aRoot, aFile1, aFolder, aFile2, aRecentFile);
        assertThat(a.getUserResourceGroup().userSeq()).isEqualTo(userA);
        assertThat(a.getUserResourceGroup().resourceSeqList()).hasSize(5);

        TrashCleanUpResourceItem file1 = item(a, aFile1);
        assertThat(file1.getUserSeq()).isEqualTo(userA);
        assertThat(file1.getResourceType()).isEqualTo(ResourceType.FILE);
        assertThat(file1.getSizeByte()).isEqualTo(100L);
        assertThat(file1.getResourceSaveName()).isEqualTo("save/a-file1");
        assertThat(file1.getResourceName()).isEqualTo("a-file1.txt");
        assertThat(file1.getResourceVersionTargets()).extracting("resourceVersionSeq", "sizeByte", "resourceSaveName")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(v1, 10L, "save/a-file1/v1"),
                        org.assertj.core.groups.Tuple.tuple(v2, 20L, "save/a-file1/v2"));

        TrashCleanUpResourceItem root = item(a, aRoot);
        assertThat(root.getResourceType()).isEqualTo(ResourceType.FOLDER);
        assertThat(root.getSizeByte()).isNull();
        assertThat(root.getResourceVersionTargets()).isNotNull().isEmpty();
        assertThat(item(a, aFile2).getResourceVersionTargets()).isEmpty();
        assertThat(item(a, aRecentFile).getResourceVersionTargets()).extracting("resourceVersionSeq").containsExactly(v3);

        assertThat(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userB, true)))
                .singleElement().satisfies(b -> {
                    assertThat(b.getTenantSeq()).isNull();
                    assertThat(b.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq).containsExactly(bFile);
                });
    }

    @Test
    @DisplayName("날짜 조건 — 휴지통 등록일 <= threshold 인 루트만(최근 루트 제외), 사용자 2명이 각각 묶임")
    void byDate_twoUsers() {
        List<TrashCleanUpTargetsData> list = repository.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(THRESHOLD, true));

        assertThat(list).extracting(TrashCleanUpTargetsData::getUserSeq).containsExactlyInAnyOrder(userA, userB);
        TrashCleanUpTargetsData a = of(list, userA);
        assertThat(a.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq)
                .containsExactlyInAnyOrder(aRoot, aFile1, aFolder, aFile2);
        assertThat(of(list, userB).getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq).containsExactly(bFile);

        assertThat(repository.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(Instant.parse("2025-12-31T00:00:00Z"), true))).isEmpty();
        assertThat(repository.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(Instant.parse("2026-12-31T00:00:00Z"), true)))
                .filteredOn(t -> t.getUserSeq() == userA).singleElement()
                .satisfies(t -> assertThat(t.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq).contains(aRecentFile));
    }

    @Test
    @DisplayName("isDelete 는 하위에만 — false 면 복원된 하위 1건, null 이면 하위 0건(루트만); 휴지통 없는 사용자는 빈 목록")
    void isDeleteOnChildrenOnly_andEmpty() {
        TrashCleanUpTargetsData notDeleted = of(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, false)), userA);
        assertThat(notDeleted.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceName)
                .containsExactlyInAnyOrder("a-root", "a-restored.txt", "a-recent.txt");

        TrashCleanUpTargetsData nullFlag = of(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, null)), userA);
        assertThat(nullFlag.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq).containsExactlyInAnyOrder(aRoot, aRecentFile);

        int nobody = user("trash-none", "없음", 1);
        assertThat(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(nobody, true))).isEmpty();
    }

    @Test
    @DisplayName("나눠 비우기 — rootLimit 은 휴지통 루트를 trash_seq 순으로 자르고 하위는 루트를 따라 전부, registeredBefore 뒤에 들어온 루트는 제외")
    void byUser_rootLimitAndRegisteredBefore() {
        Instant far = Instant.parse("2030-01-01T00:00:00Z");

        // 루트 1개 — 먼저 버린 aRoot 와 그 하위 전부, 나중 루트(aRecentFile)는 다음 묶음
        TrashCleanUpTargetsData first = of(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, true, far, 1)), userA);
        assertThat(first.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq)
                .containsExactlyInAnyOrder(aRoot, aFile1, aFolder, aFile2);

        // 첫 묶음을 지운 뒤(휴지통 행 삭제) 다음 묶음 = 남은 루트
        jdbc.update("DELETE FROM tb_trash WHERE user_seq = ? AND resource_seq = ?", userA, aRoot);
        TrashCleanUpTargetsData second = of(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, true, far, 1)), userA);
        assertThat(second.getResourceTargets()).extracting(TrashCleanUpResourceItem::getResourceSeq).containsExactly(aRecentFile);

        // 요청 시각(THRESHOLD) 뒤에 들어온 aRecentFile 은 대상이 아니다 — 남은 대상 없음
        assertThat(repository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(userA, true, THRESHOLD, 50))).isEmpty();
    }
}
