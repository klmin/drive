package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.PaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import jakarta.persistence.EntityManager;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.resource.version.query.*;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 ResourceVersion 이관 회귀 — 읽기({@link DefaultResourceVersionQueryRepository} 8 statement)·쓰기({@link DefaultResourceVersionRepository} 15 statement) — 2차 패스(10-07)로 INSERT·고정 컬럼 UPDATE·DELETE·엔티티 조회는 JPA, 동적·목록·CTE 는 JdbcClient.
 * 시드 = tenant 1, user 1 superadmin·2 admin(tenant 1)·3 anonymous(테넌트 없음). tb_resource 는 {@link ResourceRepository} 로 넣는다. 트랜잭션 롤백.
 * 검증 포인트 = INSERT 키 주입·감사 write-back, 11컬럼 엔티티 매핑(enum·LocalDateTime), 목록 UNION ALL(이력 상태 필터 + 현재 버전)·LEFT JOIN(익명 등록자)·페이지·소유 계정 가시성,
 * FOR UPDATE 잠금 순서, 버전 번호 2단계 재배열(<if> 조건), 처리 중/만료 판정(make_interval), 소유자 이전(파티션 키 변경), IN 삭제·빈 목록 가드, 저장 키·사용량.
 */
@DisplayName("DefaultResourceVersionRepository / DefaultResourceVersionQueryRepository — JPA + JdbcClient(PostgreSQL Testcontainers)")
class DefaultResourceVersionRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;
    private static final Instant V_DATE = DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0));

    @Autowired
    private ResourceVersionRepository repository;
    @Autowired
    private ResourceVersionQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void loginAsAdmin() {
        loginAs(ADMIN);
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

    private Resource file(int userSeq, String name, long size) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .resourceName(name).originalFileName(name).copyNumber(0).ext("txt").contentType("text/plain").sizeByte(size)
                .versionRegisterDate(V_DATE).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    /** 서비스와 같은 순서(W8): save(IDENTITY 즉시 INSERT, status 는 DB 기본 PENDING, 관리 상태) → initResourceSaveName(변경 감지) → (업로드 완료) 고칠 행 로드 + changeStatus → flush. */
    private ResourceVersion version(Resource file, int ownerSeq, int number, long size, ResourceVersionStatus status) {
        ResourceVersion v = ResourceVersion.builder().userSeq(file.getUserSeq()).resourceSeq(file.getResourceSeq()).ownerSeq(ownerSeq)
                .resourceSaveName(file.getResourceSaveName()).versionNumber(number).sizeByte(size).versionRegisterDate(V_DATE)
                .registerUserSeq(file.getUserSeq()).build();
        repository.save(v);
        v.initResourceSaveName();
        if (status != ResourceVersionStatus.PENDING) {
            repository.findByUserSeqAndResourceVersionSeq(file.getUserSeq(), v.getResourceVersionSeq()).changeStatus(status);
        }
        entityManager.flush();
        return v;
    }

    /** 변경 감지 결과는 DB 값으로 — 미반영 변경을 flush(커밋 대신) 후 JdbcTemplate */
    private <T> T col(long resourceVersionSeq, String column, Class<T> type) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource_version WHERE resource_version_seq = ?", type, resourceVersionSeq);
    }

    private static ResourceVersionFindQuery find(Long versionSeq, long resourceSeq, int userSeq, ResourceVersionStatus status) {
        return ResourceVersionFindQuery.builder().resourceVersionSeq(versionSeq).resourceSeq(resourceSeq).userSeq(userSeq).status(status).build();
    }

    private static ResourceVersionListQuery list(long resourceSeq, int userSeq, boolean isDelete, ResourceVersionStatus status, Integer pageIndex, Integer pageSize) {
        return ResourceVersionListQuery.builder().resourceSeq(resourceSeq).userSeq(userSeq).isDelete(isDelete).resourceVersionStatus(status)
                .page(pageIndex == null ? null : PaginationQuery.of(pageIndex, pageSize)).build();
    }

    private static ResourceVersionNumberQuery numbers(long resourceSeq, int userSeq, Integer versionNumber) {
        return ResourceVersionNumberQuery.builder().resourceSeq(resourceSeq).userSeq(userSeq).status(ResourceVersionStatus.SUCCESS).versionNumber(versionNumber).tempOffset(100000).build();
    }

    // ================================================================== INSERT·단건

    @Nested
    @DisplayName("INSERT · 엔티티 단건·목록")
    class InsertAndFind {

        @Test
        @DisplayName("save — identity 키 주입·관리 상태(Auditing 이 같은 인스턴스를 채움), 저장 이름은 변경 감지, DB 기본 status PENDING, findResourceVersion 매핑")
        void saveAndFind() {
            Resource f = file(ADMIN, "doc.txt", 10L);
            ResourceVersion v = version(f, SUPER_ADMIN, 1, 5L, ResourceVersionStatus.SUCCESS);
            assertThat(v.getResourceVersionSeq()).isPositive();
            assertThat(v.getResourceSaveName()).isEqualTo(f.getResourceSaveName() + "/" + v.getResourceVersionSeq());
            assertThat(entityManager.contains(v)).as("save 뒤 관리 상태(W8 — detach 없음)").isTrue();
            assertThat(v.getRegisterDate()).as("INSERT 감사 — Auditing 이 같은 인스턴스에").isNotNull();
            assertThat(v.getUpdateUserSeq()).as("저장 이름 변경 감지 UPDATE 의 Auditing = 컨텍스트").isEqualTo(ADMIN);
            assertThat(v.getUpdateDate()).isNotNull();
            assertThat(col(v.getResourceVersionSeq(), "status", String.class)).isEqualTo("SUCCESS");
            assertThat(col(v.getResourceVersionSeq(), "resource_save_name", String.class)).isEqualTo(v.getResourceSaveName());
            assertThat(col(v.getResourceVersionSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);

            ResourceVersion noAudit = ResourceVersion.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).ownerSeq(ADMIN).versionNumber(2).sizeByte(7L).versionRegisterDate(V_DATE).build();
            repository.save(noAudit);
            assertThat(col(noAudit.getResourceVersionSeq(), "register_user_seq", Integer.class)).as("JdbcAudit → 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(noAudit.getRegisterUserSeq()).as("Auditing — 같은 인스턴스").isEqualTo(ADMIN);
            assertThat(col(noAudit.getResourceVersionSeq(), "status", String.class)).as("INSERT 에 status 없음 → DB 기본").isEqualTo("PENDING");
            assertThat(col(noAudit.getResourceVersionSeq(), "update_user_seq", Integer.class)).isNull();

            // 단건 = 버전 키 DTO 6필드(호출부가 읽는 값 전부), 4조건 모두 맞아야
            ResourceVersionKeyData found = queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, ResourceVersionStatus.SUCCESS));
            assertThat(found).isNotNull();
            assertThat(found.getResourceVersionSeq()).isEqualTo(v.getResourceVersionSeq());
            assertThat(found.getResourceSeq()).isEqualTo(f.getResourceSeq());
            assertThat(found.getUserSeq()).isEqualTo(ADMIN);
            assertThat(found.getVersionNumber()).isEqualTo(1);
            assertThat(found.getSizeByte()).isEqualTo(5L);
            assertThat(found.getResourceSaveName()).isEqualTo(v.getResourceSaveName());

            assertThat(queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, ResourceVersionStatus.PENDING))).as("상태 불일치").isNull();
            assertThat(queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), f.getResourceSeq(), SUPER_ADMIN, ResourceVersionStatus.SUCCESS))).as("다른 소유자").isNull();
            assertThat(queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), 999999L, ADMIN, ResourceVersionStatus.SUCCESS))).as("다른 리소스").isNull();
            assertThat(queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, null))).as("null 조건은 0건").isNull();
        }

        @Test
        @DisplayName("findResourceVersionWithResource(리소스 조인 컬럼 별칭) · findAllByResource · findAllByResourceList(IN·빈 목록 가드)")
        void withResourceAndLists() {
            Resource f = file(ADMIN, "joined.txt", 100L);
            Resource other = file(ADMIN, "other.txt", 1L);
            ResourceVersion v1 = version(f, ADMIN, 1, 11L, ResourceVersionStatus.SUCCESS);
            ResourceVersion v2 = version(f, ADMIN, 2, 22L, ResourceVersionStatus.PENDING);
            ResourceVersion o1 = version(other, ADMIN, 1, 33L, ResourceVersionStatus.SUCCESS);

            ResourceVersionWithResourceData data = queryRepository.findResourceVersionWithResource(find(v1.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, ResourceVersionStatus.SUCCESS));
            assertThat(data).isNotNull();
            assertThat(data.getResourceName()).isEqualTo("joined.txt");
            assertThat(data.getResourceSaveName()).as("리소스의 저장명").isEqualTo(f.getResourceSaveName());
            assertThat(data.getVersionResourceSaveName()).as("버전의 저장명").isEqualTo(v1.getResourceSaveName());
            assertThat(data.getSizeByte()).as("리소스 크기").isEqualTo(100L);
            assertThat(data.getVersionSizeByte()).as("버전 크기").isEqualTo(11L);
            assertThat(data.getVersionNumber()).isEqualTo(1);
            assertThat(data.getStatus()).isEqualTo(ResourceVersionStatus.SUCCESS);
            assertThat(data.getVersionRegisterDate()).isEqualTo(V_DATE);
            assertThat(data.getRegisterUserSeq()).isEqualTo(ADMIN);
            assertThat(data.getRegisterDate()).isNotNull();
            assertThat(queryRepository.findResourceVersionWithResource(find(v2.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, ResourceVersionStatus.SUCCESS))).as("PENDING 은 SUCCESS 조회에 없음").isNull();

            List<ResourceVersionKeyData> byResource = queryRepository.findAllByResource(list(f.getResourceSeq(), ADMIN, false, null, null, null));
            assertThat(byResource).as("상태 무관 전부").extracting(ResourceVersionKeyData::getResourceVersionSeq).containsExactlyInAnyOrder(v1.getResourceVersionSeq(), v2.getResourceVersionSeq());
            assertThat(byResource).extracting(ResourceVersionKeyData::getSizeByte).containsExactlyInAnyOrder(11L, 22L);
            assertThat(byResource).extracting(ResourceVersionKeyData::getResourceSaveName).containsExactlyInAnyOrder(v1.getResourceSaveName(), v2.getResourceSaveName());

            ResourceVersionListQuery both = ResourceVersionListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of(f.getResourceSeq(), other.getResourceSeq())).build();
            assertThat(queryRepository.findAllByResourceList(both)).extracting(ResourceVersionKeyData::getResourceVersionSeq)
                    .containsExactlyInAnyOrder(v1.getResourceVersionSeq(), v2.getResourceVersionSeq(), o1.getResourceVersionSeq());
            assertThat(queryRepository.findAllByResourceList(ResourceVersionListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of()).build())).isEmpty();
            assertThat(queryRepository.findAllByResourceList(ResourceVersionListQuery.builder().userSeq(ADMIN).build())).as("null 목록").isEmpty();
            assertThat(queryRepository.findAllByResourceList(ResourceVersionListQuery.builder().userSeq(SUPER_ADMIN).resourceSeqList(List.of(f.getResourceSeq())).build())).as("다른 소유자").isEmpty();
        }
    }

    // ================================================================== 목록

    @Nested
    @DisplayName("findAll / countAll — 이력 UNION ALL 현재 버전·조인·페이지·가시성")
    class ListAll {

        @Test
        @DisplayName("상태 필터된 이력 + tb_resource 현재 행(CURRENT, seq NULL), version_number DESC, 익명 등록자 LEFT JOIN, OFFSET 페이지, is_delete·소유 계정 DELETED 는 0")
        void findAllAndCount() {
            Resource f = file(ADMIN, "list.txt", 100L);
            jdbc.update("UPDATE tb_resource SET current_version_number = 4 WHERE resource_seq = ?", f.getResourceSeq());
            ResourceVersion v1 = version(f, ANONYMOUS, 1, 1L, ResourceVersionStatus.SUCCESS);
            ResourceVersion v2 = version(f, SUPER_ADMIN, 2, 2L, ResourceVersionStatus.SUCCESS);
            ResourceVersion v3 = version(f, ADMIN, 3, 3L, ResourceVersionStatus.SUCCESS);
            version(f, ADMIN, 4, 4L, ResourceVersionStatus.PENDING);

            ResourceVersionListQuery page1 = list(f.getResourceSeq(), ADMIN, false, ResourceVersionStatus.SUCCESS, 1, 10);
            assertThat(queryRepository.countAll(page1)).as("SUCCESS 이력 3 + 현재 1").isEqualTo(4L);
            List<ResourceVersionData> rows = queryRepository.findAll(page1);
            assertThat(rows).extracting(ResourceVersionData::getVersionNumber).containsExactly(4, 3, 2, 1);

            ResourceVersionData current = rows.get(0);
            assertThat(current.getVersionType()).isEqualTo(ResourceVersionType.CURRENT);
            assertThat(current.getResourceVersionSeq()).as("현재 버전은 seq 없음(CAST NULL)").isNull();
            assertThat(current.getSizeByte()).isEqualTo(100L);
            assertThat(current.getOwnerSeq()).isEqualTo(ADMIN);
            assertThat(current.getUserSeq()).isEqualTo(ADMIN);
            assertThat(current.getUserName()).isEqualTo("관리자");
            assertThat(current.getTenantSeq()).isEqualTo(1);
            assertThat(current.getTenantName()).isEqualTo("기본 테넌트");
            assertThat(current.getVersionRegisterDate()).isEqualTo(V_DATE);

            ResourceVersionData history = rows.get(1);
            assertThat(history.getVersionType()).isEqualTo(ResourceVersionType.HISTORY);
            assertThat(history.getResourceVersionSeq()).isEqualTo(v3.getResourceVersionSeq());
            assertThat(history.getOwnerName()).isEqualTo("관리자");
            assertThat(history.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(history.getOwnerTenantSeq()).isEqualTo(1);
            assertThat(history.getOwnerTenantName()).isEqualTo("기본 테넌트");

            assertThat(rows.get(2).getResourceVersionSeq()).isEqualTo(v2.getResourceVersionSeq());
            assertThat(rows.get(2).getOwnerSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(rows.get(2).getOwnerTenantSeq()).as("슈퍼어드민은 테넌트 없음").isNull();

            ResourceVersionData anonymous = rows.get(3);
            assertThat(anonymous.getResourceVersionSeq()).isEqualTo(v1.getResourceVersionSeq());
            assertThat(anonymous.getOwnerSeq()).isEqualTo(ANONYMOUS);
            assertThat(anonymous.getOwnerName()).as("익명 등록자도 LEFT JOIN 으로 행 유지").isEqualTo("익명 사용자");
            assertThat(anonymous.getOwnerTenantName()).isNull();

            assertThat(queryRepository.findAll(list(f.getResourceSeq(), ADMIN, false, ResourceVersionStatus.SUCCESS, 2, 3)))
                    .as("2페이지(OFFSET 3)").extracting(ResourceVersionData::getVersionNumber).containsExactly(1);
            assertThat(queryRepository.countAll(list(f.getResourceSeq(), ADMIN, false, ResourceVersionStatus.PENDING, 1, 10))).as("PENDING 이력 1 + 현재 1").isEqualTo(2L);
            assertThat(queryRepository.countAll(list(f.getResourceSeq(), ADMIN, true, ResourceVersionStatus.SUCCESS, 1, 10))).as("휴지통 기준엔 없음").isZero();
            assertThat(queryRepository.countAll(list(f.getResourceSeq(), SUPER_ADMIN, false, ResourceVersionStatus.SUCCESS, 1, 10))).as("다른 드라이브").isZero();

            jdbc.update("UPDATE tb_user SET status = 'INACTIVE' WHERE user_seq = ?", ADMIN);
            assertThat(queryRepository.countAll(page1)).as("INACTIVE 소유 계정은 보임").isEqualTo(4L);
            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ADMIN);
            assertThat(queryRepository.countAll(page1)).as("DELETED 소유 계정은 숨김").isZero();
            assertThat(queryRepository.findAll(page1)).isEmpty();
        }
    }

    // ================================================================== 쓰기

    @Nested
    @DisplayName("쓰기 — 잠금·버전 번호 재배열·상태·처리 중/만료·소유자 이전·삭제")
    class Writes {

        @Test
        @DisplayName("lockAllVersionsByResource(값, version_number 순) · temporary→decrease(versionNumber 초과만) · temporary→increase(전체) · 상태 변경 감지")
        void lockAndShiftNumbers() {
            Resource f = file(ADMIN, "shift.txt", 1L);
            ResourceVersion v1 = version(f, ADMIN, 1, 1L, ResourceVersionStatus.SUCCESS);
            ResourceVersion v2 = version(f, ADMIN, 2, 2L, ResourceVersionStatus.SUCCESS);
            ResourceVersion v3 = version(f, ADMIN, 3, 3L, ResourceVersionStatus.SUCCESS);

            List<ResourceVersionRowData> locked = repository.lockAllVersionsByResource(ADMIN, f.getResourceSeq());
            assertThat(locked).extracting(ResourceVersionRowData::getVersionNumber).containsExactly(1, 2, 3);
            assertThat(locked.get(0).getResourceSaveName()).isEqualTo(v1.getResourceSaveName());
            assertThat(locked.get(0).getStatus()).isEqualTo(ResourceVersionStatus.SUCCESS);
            assertThat(locked.get(0).getOwnerSeq()).isEqualTo(ADMIN);
            assertThat(locked.get(0).getVersionRegisterDate()).isEqualTo(V_DATE);
            assertThat(repository.lockAllVersionsByResource(SUPER_ADMIN, f.getResourceSeq())).isEmpty();

            // 2번 삭제 후 3번만 한 칸 내림 — <if> versionNumber > 2
            assertThat(repository.delete(ResourceVersionDeleteQuery.builder().resourceVersionSeq(v2.getResourceVersionSeq()).userSeq(ADMIN).build())).isEqualTo(1);
            ResourceVersionNumberQuery after2 = numbers(f.getResourceSeq(), ADMIN, 2);
            assertThat(repository.temporaryVersionNumber(after2)).isEqualTo(1);
            assertThat(col(v3.getResourceVersionSeq(), "version_number", Integer.class)).isEqualTo(100003);
            assertThat(repository.decreaseAllVersionNumbers(after2)).isEqualTo(1);
            assertThat(col(v3.getResourceVersionSeq(), "version_number", Integer.class)).isEqualTo(2);
            assertThat(col(v1.getResourceVersionSeq(), "version_number", Integer.class)).as("2 이하는 그대로").isEqualTo(1);
            assertThat(col(v3.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("쿼리 객체 감사 비면 컨텍스트").isEqualTo(ADMIN);
            assertThat(col(v3.getResourceVersionSeq(), "update_date", Object.class)).isNotNull();

            // versionNumber 없음 → 전부 한 칸 올림
            ResourceVersionNumberQuery all = numbers(f.getResourceSeq(), ADMIN, null);
            assertThat(repository.temporaryVersionNumber(all)).isEqualTo(2);
            assertThat(repository.increaseAllVersionNumbers(all)).isEqualTo(2);
            assertThat(col(v1.getResourceVersionSeq(), "version_number", Integer.class)).isEqualTo(2);
            assertThat(col(v3.getResourceVersionSeq(), "version_number", Integer.class)).isEqualTo(3);
            assertThat(repository.temporaryVersionNumber(ResourceVersionNumberQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).status(ResourceVersionStatus.PENDING).tempOffset(100000).build())).as("상태 불일치").isZero();

            // 상태 = 고칠 행 로드 + changeStatus(변경 감지) — 위 번호 이동(JPQL) 뒤라 관리 인스턴스의 번호는 옛 값이지만 상태 컬럼만 UPDATE(@DynamicUpdate)
            repository.findByUserSeqAndResourceVersionSeq(ADMIN, v3.getResourceVersionSeq()).changeStatus(ResourceVersionStatus.DELETE);
            assertThat(col(v3.getResourceVersionSeq(), "status", String.class)).isEqualTo("DELETE");
            assertThat(col(v3.getResourceVersionSeq(), "version_number", Integer.class)).as("번호 이동 결과를 덮어쓰지 않음").isEqualTo(3);
            assertThat(col(v3.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(repository.findByUserSeqAndResourceVersionSeq(SUPER_ADMIN, v3.getResourceVersionSeq())).as("다른 소유자(파티션 키)").isNull();
        }

        @Test
        @DisplayName("updateUserSeqForResource(s)(파티션 키 변경) · delete 4종(IN·빈 목록 가드) · findStorageKeysByUserSeqList · findUsageSummaryByUserSeq")
        void ownerTransferDeletesAndSummary() {
            Resource a = file(ADMIN, "a.txt", 10L);
            Resource b = file(ADMIN, "b.txt", 20L);
            ResourceVersion a1 = version(a, ADMIN, 1, 1L, ResourceVersionStatus.SUCCESS);
            ResourceVersion a2 = version(a, ADMIN, 2, 2L, ResourceVersionStatus.SUCCESS);
            ResourceVersion b1 = version(b, ADMIN, 1, 4L, ResourceVersionStatus.SUCCESS);

            assertThat(queryRepository.findStorageKeysByUserSeqList(List.of(ADMIN))).extracting(k -> k.getResourceSaveName())
                    .containsExactlyInAnyOrder(a1.getResourceSaveName(), a2.getResourceSaveName(), b1.getResourceSaveName());
            assertThat(queryRepository.findStorageKeysByUserSeqList(List.of())).isEmpty();

            var usage = queryRepository.findUsageSummaryByUserSeq(ADMIN);
            assertThat(usage.totalBytes()).isEqualTo(7L);
            assertThat(usage.liveBytes()).isEqualTo(7L);
            assertThat(usage.trashBytes()).isZero();
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", b.getResourceSeq());
            usage = queryRepository.findUsageSummaryByUserSeq(ADMIN);
            assertThat(usage.liveBytes()).isEqualTo(3L);
            assertThat(usage.trashBytes()).isEqualTo(4L);
            assertThat(queryRepository.findUsageSummaryByUserSeq(99999).totalBytes()).isZero();

            assertThat(repository.updateUserSeqForResource(ResourceVersionUserUpdateQuery.builder().resourceSeq(a.getResourceSeq()).userSeq(ADMIN).changeUserSeq(SUPER_ADMIN).build())).isEqualTo(2);
            assertThat(col(a1.getResourceVersionSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
            assertThat(col(a1.getResourceVersionSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(repository.updateUserSeqForResources(ResourcesVersionUserUpdateQuery.builder().resourceSeqList(List.of(b.getResourceSeq())).userSeq(ADMIN).changeUserSeq(SUPER_ADMIN).build())).isEqualTo(1);
            assertThat(repository.updateUserSeqForResources(ResourcesVersionUserUpdateQuery.builder().resourceSeqList(List.of()).userSeq(ADMIN).changeUserSeq(SUPER_ADMIN).build())).as("빈 목록").isZero();
            assertThat(col(b1.getResourceVersionSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
            assertThat(queryRepository.findStorageKeysByUserSeqList(List.of(ADMIN))).as("이전 뒤 ADMIN 소유 없음").isEmpty();

            assertThat(repository.deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq())).build())).as("옛 소유자로는 0").isZero();
            assertThat(repository.deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery.builder().userSeq(SUPER_ADMIN).resourceSeqList(List.of(a.getResourceSeq())).build())).isEqualTo(2);
            assertThat(repository.deleteByResourceSeqList(ResourceVersionDeleteSeqListQuery.builder().userSeq(SUPER_ADMIN).resourceSeqList(List.of()).build())).as("빈 목록").isZero();
            assertThat(repository.deleteByResource(ResourceVersionDeleteQuery.builder().resourceSeq(b.getResourceSeq()).userSeq(SUPER_ADMIN).build())).isEqualTo(1);
            assertThat(repository.deleteByResource(ResourceVersionDeleteQuery.builder().resourceSeq(b.getResourceSeq()).userSeq(SUPER_ADMIN).build())).as("이미 없음").isZero();

            version(a, ADMIN, 3, 1L, ResourceVersionStatus.SUCCESS);
            assertThat(repository.deleteByUserSeqList(List.of(ADMIN))).isEqualTo(1);
            assertThat(repository.deleteByUserSeqList(List.of())).as("빈 목록").isZero();
            assertThat(queryRepository.findAllByResource(list(a.getResourceSeq(), ADMIN, false, null, null, null))).isEmpty();
        }
    }

    // ================================================================== JPA 영속성·감사(2차 패스)

    @Nested
    @DisplayName("JPA — 저장 후 관리 상태 + 저장 이름 변경 감지·잠금 결과는 값(컨텍스트 밖)·Query 조회는 DTO·status/저장 이름은 INSERT 에서 빠짐·시큐리티 없음 감사")
    class JpaLifecycle {

        @Test
        @DisplayName("save 는 status·resource_save_name 을 넣지 않고(DB DEFAULT PENDING) 관리 상태로 남김 → initResourceSaveName 이 변경 감지 UPDATE(저장 이름 컬럼만, status 는 그대로) · 잠금 결과는 값")
        void savedEntityStaysManaged() {
            Resource f = file(ADMIN, "jpa-ver.txt", 5L);
            ResourceVersion v = ResourceVersion.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).ownerSeq(ADMIN)
                    .resourceSaveName("orig").versionNumber(1).sizeByte(5L).status(ResourceVersionStatus.PENDING).versionRegisterDate(V_DATE).build();
            repository.save(v);
            assertThat(v.getResourceVersionSeq()).isNotNull();
            assertThat(entityManager.contains(v)).as("W8 — save 뒤 관리 상태").isTrue();
            assertThat(col(v.getResourceVersionSeq(), "status", String.class)).as("INSERT 에서 빠짐 → DB DEFAULT").isEqualTo("PENDING");
            assertThat(col(v.getResourceVersionSeq(), "resource_save_name", String.class)).isNull();
            assertThat(col(v.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("INSERT 때 update_* 없음").isNull();

            v.initResourceSaveName();
            assertThat(col(v.getResourceVersionSeq(), "resource_save_name", String.class)).as("변경 감지 UPDATE").isEqualTo("orig/" + v.getResourceVersionSeq());
            assertThat(col(v.getResourceVersionSeq(), "status", String.class)).as("status 는 바꾸지 않았으니 그대로").isEqualTo("PENDING");
            assertThat(col(v.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("Auditing").isEqualTo(ADMIN);

            List<ResourceVersionRowData> locked = repository.lockAllVersionsByResource(ADMIN, f.getResourceSeq());
            assertThat(locked).hasSize(1);
            assertThat(locked.get(0).getResourceSaveName()).as("잠금 결과는 DB 값(값 객체 — 영속성 컨텍스트 밖)").isEqualTo("orig/" + v.getResourceVersionSeq());
            assertThat(locked.get(0).getStatus()).isEqualTo(ResourceVersionStatus.PENDING);
        }

        @Test
        @DisplayName("Query 조회(DTO)는 같은 트랜잭션의 관리 엔티티를 건드리지 않고 DB 현재 값 — @Modifying 으로 바뀐 버전 번호를 본다(엔티티 반환 시절엔 캐시의 옛 인스턴스를 돌려주고 detach 했음)")
        void queryReadsDbValueWithoutTouchingManagedEntity() {
            Resource f = file(ADMIN, "jpa-ver-fresh.txt", 5L);
            ResourceVersion v = version(f, ADMIN, 1, 5L, ResourceVersionStatus.SUCCESS);

            ResourceVersion managed = entityManager.find(ResourceVersion.class, v.getResourceVersionSeq());
            assertThat(managed.getVersionNumber()).isEqualTo(1);
            assertThat(repository.temporaryVersionNumber(numbers(f.getResourceSeq(), ADMIN, null))).isEqualTo(1);
            assertThat(repository.increaseAllVersionNumbers(numbers(f.getResourceSeq(), ADMIN, null))).isEqualTo(1);

            assertThat(queryRepository.findAllByResource(list(f.getResourceSeq(), ADMIN, false, null, null, null)))
                    .extracting(ResourceVersionKeyData::getVersionNumber).as("DB 현재 값").containsExactly(2);
            assertThat(queryRepository.findResourceVersion(find(v.getResourceVersionSeq(), f.getResourceSeq(), ADMIN, ResourceVersionStatus.SUCCESS)).getVersionNumber()).isEqualTo(2);
            assertThat(queryRepository.findAllByResourceList(ResourceVersionListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of(f.getResourceSeq())).build()))
                    .extracting(ResourceVersionKeyData::getVersionNumber).containsExactly(2);
            assertThat(entityManager.contains(managed)).as("Query 조회가 관리 엔티티를 떼어내지 않음").isTrue();
            assertThat(managed.getVersionNumber()).as("관리 엔티티는 @Modifying 을 모름(캐시) — Query 결과와 무관").isEqualTo(1);
        }

        @Test
        @DisplayName("시큐리티 없음(웹오피스·스케줄러) — save 는 선언한 등록자, 상태 변경 감지는 수정자를 바꾸지 않고(이전 값 유지) 수정일만, 버전 번호 이동(JPQL)은 선언한 수정자 그대로(없으면 null)")
        void auditWithoutSecurityContext() {
            Resource f = file(ADMIN, "jpa-ver-nosec.txt", 5L);
            SecurityContextHolder.clearContext();
            ResourceVersion v = ResourceVersion.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).ownerSeq(ADMIN)
                    .versionNumber(1).sizeByte(5L).versionRegisterDate(V_DATE).registerUserSeq(SUPER_ADMIN).build();
            repository.save(v);
            assertThat(col(v.getResourceVersionSeq(), "register_user_seq", Integer.class)).as("선언 값 그대로").isEqualTo(SUPER_ADMIN);

            // W8 — 상태는 변경 감지. 구 JPQL(선언 수정자 없으면 NULL 로 덮음)과 달리 Auditing 은 감사자가 없으면 수정자를 건드리지 않는다
            repository.findByUserSeqAndResourceVersionSeq(ADMIN, v.getResourceVersionSeq()).changeStatus(ResourceVersionStatus.SUCCESS);
            assertThat(col(v.getResourceVersionSeq(), "status", String.class)).isEqualTo("SUCCESS");
            assertThat(col(v.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("이전 값(INSERT 때 없음) 유지").isNull();
            assertThat(col(v.getResourceVersionSeq(), "update_date", Object.class)).as("수정일은 Auditing 현재 시각").isNotNull();

            assertThat(repository.temporaryVersionNumber(numbers(f.getResourceSeq(), ADMIN, null))).isEqualTo(1);
            assertThat(col(v.getResourceVersionSeq(), "update_user_seq", Integer.class)).as("선언도 시큐리티도 없으면 null").isNull();
            assertThat(repository.increaseAllVersionNumbers(numbers(f.getResourceSeq(), ADMIN, null))).isEqualTo(1);
            assertThat(col(v.getResourceVersionSeq(), "version_number", Integer.class)).as("(1 + offset) - offset + 1").isEqualTo(2);
        }
    }
}
