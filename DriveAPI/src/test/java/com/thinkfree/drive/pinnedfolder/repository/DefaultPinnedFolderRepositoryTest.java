package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.pinnedfolder.query.*;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 PinnedFolder 이관 회귀 — 읽기({@link DefaultPinnedFolderQueryRepository}: 목록·존재)·쓰기({@link DefaultPinnedFolderRepository}: INSERT 는 JPA saveAndFlush, DELETE 11종은 전부 JPQL {@code @Modifying}, FOR UPDATE 건수만 JdbcClient).
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin(tenant 1)·3 anonymous. 리소스는 {@link ResourceRepository}, 공유는 {@link ShareRepository}, 수신자는 insertUserWithSeatCheck. 트랜잭션 롤백.
 * 검증 포인트 = JPA INSERT(identity 키·리스너 register_date Instant·uniq_pinned_folder 위반), 목록(등록 순·리소스 조인)·존재, FOR UPDATE 건수, DELETE 11종(선택 제외 목록·항목별 루프·테넌트 3종)·빈 목록 가드.
 */
@DisplayName("DefaultPinnedFolderRepository / DefaultPinnedFolderQueryRepository — JPA insert + JdbcClient(PostgreSQL Testcontainers)")
class DefaultPinnedFolderRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private PinnedFolderRepository repository;
    @Autowired
    private PinnedFolderQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Resource fa, fb, fc;
    private User t1, t2;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        fa = folder("alpha");
        fb = folder("beta");
        fc = folder("gamma");
        t1 = user("pin-t1");
        t2 = user("pin-t2");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private User user(String userId) {
        User u = User.createUser(userId, 1, null, SignupType.LOCAL, "수신자 " + userId, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u;
    }

    private Resource folder(String name) {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).resourceType(ResourceType.FOLDER).resourceName(name).originalFileName(name)
                .fullPathSeq("").fullPath("").copyNumber(0).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        return r;
    }

    /** 고정: userSeq 가 sharedBy(소유자 ADMIN)의 폴더를 고정. */
    private PinnedFolder pin(int userSeq, Resource resource) {
        PinnedFolder p = PinnedFolder.builder().userSeq(userSeq).resourceSeq(resource.getResourceSeq()).sharedByUserSeq(ADMIN).build();
        repository.insertPinnedFolder(p);
        return p;
    }

    private int count(int sharedBy) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_pinned_folder WHERE shared_by_user_seq = ?", Integer.class, sharedBy);
    }

    private static PinnedFolderExistsQuery exists(int userSeq, Resource r) {
        return PinnedFolderExistsQuery.builder().userSeq(userSeq).resourceSeq(r.getResourceSeq()).sharedByUserSeq(ADMIN).build();
    }

    @Test
    @DisplayName("insertPinnedFolder(JPA) — identity 키·register_date(Instant, 리스너)·목록 등록 순·리소스 조인·존재 여부·FOR UPDATE 건수, uniq_pinned_folder 위반은 맨 끝")
    void insertAndFind() {
        Instant before = Instant.now().minusSeconds(1);
        PinnedFolder p1 = pin(ADMIN, fb);
        PinnedFolder p2 = pin(ADMIN, fa);
        pin(t1.getUserSeq(), fa);
        assertThat(p1.getPinnedFolderSeq()).isPositive();
        assertThat(p1.getRegisterDate()).as("JpaAuditListener 가 Instant 로 채움").isNotNull().isAfter(before);
        assertThat(jdbc.queryForObject("SELECT register_date FROM tb_pinned_folder WHERE pinned_folder_seq = ?", java.sql.Timestamp.class, p1.getPinnedFolderSeq()).toInstant()).isEqualTo(p1.getRegisterDate());

        List<PinnedFolderData> rows = queryRepository.findPinnedFolders(PinnedFolderListQuery.builder().userSeq(ADMIN).build());
        assertThat(rows).extracting(PinnedFolderData::getResourceName).as("등록 순(이름 아님)").containsExactly("beta", "alpha");
        assertThat(rows.get(0).getPinnedFolderSeq()).isEqualTo(p1.getPinnedFolderSeq());
        assertThat(rows.get(0).getResourceSeq()).isEqualTo(fb.getResourceSeq());
        assertThat(rows.get(0).getSharedByUserSeq()).isEqualTo(ADMIN);
        assertThat(rows.get(0).getResourceType()).isEqualTo(ResourceType.FOLDER);
        assertThat(queryRepository.findPinnedFolders(PinnedFolderListQuery.builder().userSeq(t1.getUserSeq()).build())).extracting(PinnedFolderData::getResourceName).containsExactly("alpha");
        assertThat(queryRepository.findPinnedFolders(PinnedFolderListQuery.builder().userSeq(t2.getUserSeq()).build())).isEmpty();

        assertThat(queryRepository.existsPinnedFolder(exists(ADMIN, fa))).isTrue();
        assertThat(queryRepository.existsPinnedFolder(exists(ADMIN, fc))).isFalse();
        assertThat(queryRepository.existsPinnedFolder(PinnedFolderExistsQuery.builder().userSeq(ADMIN).resourceSeq(fa.getResourceSeq()).sharedByUserSeq(SUPER_ADMIN).build())).as("공유자 다름").isFalse();

        assertThat(repository.countByUserForUpdate(PinnedFolderCountQuery.builder().userSeq(ADMIN).build())).isEqualTo(2);
        assertThat(repository.countByUserForUpdate(PinnedFolderCountQuery.builder().userSeq(t2.getUserSeq()).build())).isZero();

        assertThat(repository.deletePinnedFolder(PinnedFolderDeleteQuery.builder().userSeq(t1.getUserSeq()).pinnedFolderSeq(p2.getPinnedFolderSeq()).build())).as("다른 사용자").isZero();
        assertThat(repository.deletePinnedFolder(PinnedFolderDeleteQuery.builder().userSeq(ADMIN).pinnedFolderSeq(p2.getPinnedFolderSeq()).build())).isEqualTo(1);
        assertThat(queryRepository.existsPinnedFolder(exists(ADMIN, fa))).isFalse();

        // 유니크 위반은 PG 트랜잭션을 abort 시키므로 맨 끝에서 확인
        assertThatThrownBy(() -> pin(ADMIN, fb)).as("uniq_pinned_folder(user, resource, shared_by)").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteByUserResources 배치(B2) — 같은 길이(t1·t2: fa,fb)·다른 길이(t3: fc)·빈 목록 섞기, 목록 밖 리소스·다른 공유자 행은 남음")
    void deleteByUserResources_batch() {
        User t3 = user("pin-t3");
        for (User t : List.of(t1, t2, t3)) {
            pin(t.getUserSeq(), fa);
            pin(t.getUserSeq(), fb);
            pin(t.getUserSeq(), fc);
        }
        repository.insertPinnedFolder(PinnedFolder.builder().userSeq(t1.getUserSeq()).resourceSeq(fa.getResourceSeq()).sharedByUserSeq(SUPER_ADMIN).build());

        repository.deleteByUserResources(List.of(
                new PinnedFolderDeleteUserResourcesQuery(t1.getUserSeq(), ADMIN, List.of(fa.getResourceSeq(), fb.getResourceSeq())),
                new PinnedFolderDeleteUserResourcesQuery(t3.getUserSeq(), ADMIN, List.of(fc.getResourceSeq())),
                new PinnedFolderDeleteUserResourcesQuery(t2.getUserSeq(), ADMIN, List.of(fa.getResourceSeq(), fb.getResourceSeq())),
                new PinnedFolderDeleteUserResourcesQuery(t2.getUserSeq(), ADMIN, List.of())));

        assertThat(jdbc.queryForList("SELECT user_seq || ':' || resource_seq FROM tb_pinned_folder WHERE shared_by_user_seq = ?", String.class, ADMIN))
                .as("t1:fc, t2:fc, t3:fa, t3:fb 만 남음")
                .containsExactlyInAnyOrder(t1.getUserSeq() + ":" + fc.getResourceSeq(), t2.getUserSeq() + ":" + fc.getResourceSeq(),
                        t3.getUserSeq() + ":" + fa.getResourceSeq(), t3.getUserSeq() + ":" + fb.getResourceSeq());
        assertThat(count(SUPER_ADMIN)).as("다른 공유자 행은 그대로").isEqualTo(1);
    }

    @Test
    @DisplayName("deleteByResource(s) · ExcludingOwner(선택 제외 목록) · deleteByUserResources(배치) · 수신자/공유자 IN · 빈 목록 가드")
    void deletesByResourceAndUsers() {
        pin(ADMIN, fa);
        pin(t1.getUserSeq(), fa);
        pin(t2.getUserSeq(), fa);
        pin(t1.getUserSeq(), fb);
        pin(t2.getUserSeq(), fb);
        pin(t1.getUserSeq(), fc);
        assertThat(count(ADMIN)).isEqualTo(6);

        assertThat(repository.deleteByResourcesExcludingOwner(new PinnedFolderDeleteResourcesQuery(ADMIN, ADMIN, List.of(), null, null))).isZero();
        assertThat(repository.deleteByResourcesExcludingOwner(new PinnedFolderDeleteResourcesQuery(ADMIN, ADMIN, List.of(fa.getResourceSeq()), List.of(t1.getUserSeq(), t2.getUserSeq()), null))).as("둘 다 제외").isZero();
        assertThat(repository.deleteByResourcesExcludingOwner(new PinnedFolderDeleteResourcesQuery(ADMIN, ADMIN, List.of(fa.getResourceSeq()), List.of(t1.getUserSeq()), null))).as("t2 만").isEqualTo(1);
        assertThat(repository.deleteByResourcesExcludingOwner(new PinnedFolderDeleteResourcesQuery(ADMIN, ADMIN, List.of(fa.getResourceSeq()), null, null))).as("t1, 소유자 제외").isEqualTo(1);
        assertThat(count(ADMIN)).as("fa:ADMIN, fb:t1,t2, fc:t1").isEqualTo(4);

        repository.deleteByUserResources(List.of(
                new PinnedFolderDeleteUserResourcesQuery(t1.getUserSeq(), ADMIN, List.of(fb.getResourceSeq(), fc.getResourceSeq())),
                new PinnedFolderDeleteUserResourcesQuery(t2.getUserSeq(), ADMIN, List.of())));
        repository.deleteByUserResources(List.of());
        assertThat(count(ADMIN)).as("fa:ADMIN, fb:t2").isEqualTo(2);

        assertThat(repository.deleteByResource(new PinnedFolderDeleteResourceQuery(null, ADMIN, fb.getResourceSeq()))).isEqualTo(1);
        pin(t1.getUserSeq(), fb);
        assertThat(repository.deleteByResources(new PinnedFolderDeleteResourcesQuery(null, ADMIN, List.of(), null, null))).isZero();
        assertThat(repository.deleteByResources(new PinnedFolderDeleteResourcesQuery(null, ADMIN, List.of(fb.getResourceSeq(), 99999L), null, null))).isEqualTo(1);

        pin(t2.getUserSeq(), fc);
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(t2.getUserSeq()))).isEqualTo(1);
        assertThat(repository.deleteBySharedByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteBySharedByUserSeqList(List.of(ADMIN))).as("fa:ADMIN").isEqualTo(1);
        assertThat(count(ADMIN)).isZero();
    }

    @Test
    @DisplayName("테넌트 3종(자기 제외·공유 유형·외부 접근자) · deleteOtherTenantByResources(선택 제외 목록)")
    void tenantDeletes() {
        shareRepository.save(Share.builder().userSeq(ADMIN).resourceSeq(fa.getResourceSeq()).shareType(ShareType.ORG_LINK).isPasswordEnabled(false).build());
        shareRepository.save(Share.builder().userSeq(ADMIN).resourceSeq(fb.getResourceSeq()).shareType(ShareType.SPECIFIC_USERS).isPasswordEnabled(false).build());
        pin(ADMIN, fa);
        pin(t1.getUserSeq(), fa);
        pin(t2.getUserSeq(), fa);
        pin(SUPER_ADMIN, fa);
        pin(t1.getUserSeq(), fb);
        pin(SUPER_ADMIN, fc);
        assertThat(count(ADMIN)).isEqualTo(6);

        assertThat(repository.deleteOtherTenantByResources(new PinnedFolderDeleteResourcesQuery(null, ADMIN, List.of(), null, 1))).isZero();
        assertThat(repository.deleteOtherTenantByResources(new PinnedFolderDeleteResourcesQuery(null, ADMIN, List.of(fc.getResourceSeq()), List.of(SUPER_ADMIN), 1))).as("제외 목록").isZero();
        assertThat(repository.deleteOtherTenantByResources(new PinnedFolderDeleteResourcesQuery(null, ADMIN, List.of(fc.getResourceSeq()), null, 1))).as("fc 의 superadmin(테넌트 밖)").isEqualTo(1);
        assertThat(repository.deleteOtherTenantSharedByTenant(1)).as("fa 의 superadmin").isEqualTo(1);
        assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of())).isZero();
        assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of("ORG_LINK"))).as("fa(ORG_LINK) 의 t1·t2, 자기 자신 제외").isEqualTo(2);
        assertThat(count(ADMIN)).as("fa:ADMIN, fb:t1").isEqualTo(2);
        assertThat(repository.deleteAllSharedByTenant(1)).as("자기 자신 제외 → fb 의 t1").isEqualTo(1);
        assertThat(count(ADMIN)).isEqualTo(1);
    }
}
