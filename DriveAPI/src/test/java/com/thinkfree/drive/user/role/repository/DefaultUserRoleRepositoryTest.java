package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.role.entity.UserRole;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static com.thinkfree.drive.jooq.Tables.TB_USER_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * tb_user_role 쓰기({@link DefaultUserRoleRepository}: jOOQ batch INSERT·DELETE)·읽기({@link DefaultUserRoleQueryRepository}: JPQL 스칼라·튜플),
 * jOOQ 생성 코드의 role_type → {@link RoleType} 매핑. 시드(initialize.sql) = 1 SUPER_ADMIN·2 ADMIN·3 ANONYMOUS. 트랜잭션 롤백.
 * PK 위반은 PG 트랜잭션을 abort 시키므로 맨 끝.
 */
@DisplayName("DefaultUserRoleRepository / DefaultUserRoleQueryRepository — JPA(PostgreSQL Testcontainers) + jOOQ enum 매핑")
class DefaultUserRoleRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;
    private static final int TENANT = 1;

    @Autowired
    private UserRoleRepository repository;
    @Autowired
    private UserRoleQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DSLContext dsl;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private int insertUser(String userId) {
        User u = User.createUser(userId, TENANT, null, SignupType.LOCAL, userId, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        assertThat(userRepository.insertUserWithSeatCheck(u, false, 0)).isTrue();
        return u.getUserSeq();
    }

    @Test
    @DisplayName("시드 역할 — 1 SUPER_ADMIN·2 ADMIN·3 ANONYMOUS, holds·findRolesByUserSeqs(행 없는 사용자는 키 없음)")
    void seedRoles() {
        assertThat(queryRepository.findRoles(SUPER_ADMIN)).containsExactly(RoleType.SUPER_ADMIN);
        assertThat(queryRepository.findRoles(ADMIN)).containsExactly(RoleType.ADMIN);
        assertThat(queryRepository.findRoles(ANONYMOUS)).containsExactly(RoleType.ANONYMOUS);
        assertThat(queryRepository.findRoles(999999)).isEmpty();

        assertThat(queryRepository.holds(ADMIN, RoleType.ADMIN)).isTrue();
        assertThat(queryRepository.holds(ADMIN, RoleType.USER)).isFalse();

        Map<Integer, java.util.Set<RoleType>> byUser = queryRepository.findRolesByUserSeqs(List.of(SUPER_ADMIN, ADMIN, 999999));
        assertThat(byUser).containsOnlyKeys(SUPER_ADMIN, ADMIN);
        assertThat(byUser.get(ADMIN)).containsExactly(RoleType.ADMIN);
        assertThat(queryRepository.findRolesByUserSeqs(List.of())).isEmpty();
        assertThat(queryRepository.findRolesByUserSeqs(null)).isEmpty();
    }

    @Test
    @DisplayName("grant(jOOQ — 즉시 DB 반영) → findRoles 여러 역할(enum 순서) → revoke(1/0)·같은 트랜잭션 재부여 / grantAll / 빈·널 no-op / deleteByUserSeqList / FK 없음 / PK 중복은 맨 끝")
    void grantRevokeFlow() {
        int user = insertUser("ur-a@test.com");

        repository.grant(UserRole.grant(user, RoleType.USER, SUPER_ADMIN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_user_role WHERE user_seq = ? AND role_type = 'USER'", Integer.class, user))
                .as("영속성 컨텍스트를 거치지 않고 바로 DB 반영").isEqualTo(1);

        repository.grant(UserRole.grant(user, RoleType.ADMIN, SUPER_ADMIN));
        assertThat(queryRepository.findRoles(user)).as("같은 트랜잭션의 JPQL 조회에 바로 보임").containsExactly(RoleType.ADMIN, RoleType.USER);
        assertThat(queryRepository.holds(user, RoleType.USER)).isTrue();

        assertThat(repository.revoke(user, RoleType.USER)).isEqualTo(1);
        assertThat(repository.revoke(user, RoleType.USER)).as("없던 역할").isZero();
        assertThat(queryRepository.findRoles(user)).containsExactly(RoleType.ADMIN);

        // 역할 변경 = 회수 후 부여 — 같은 트랜잭션에서 같은 키 재부여
        repository.grant(UserRole.grant(user, RoleType.USER, SUPER_ADMIN));
        assertThat(queryRepository.findRoles(user)).containsExactly(RoleType.ADMIN, RoleType.USER);
        assertThat(repository.revoke(user, RoleType.USER)).isEqualTo(1);

        int other = insertUser("ur-b@test.com");
        repository.grantAll(List.of(UserRole.grant(other, RoleType.USER, SUPER_ADMIN), UserRole.grant(other, RoleType.ADMIN, SUPER_ADMIN)));
        repository.grantAll(List.of());
        repository.grantAll(null);
        assertThat(queryRepository.findRolesByUserSeqs(List.of(user, other)))
                .containsEntry(user, java.util.EnumSet.of(RoleType.ADMIN))
                .containsEntry(other, java.util.EnumSet.of(RoleType.ADMIN, RoleType.USER));

        // 계정 완전 삭제 cascade — 대상 계정 역할 행 전부(DELETE … IN 한 문장), 다른 계정·시드는 그대로, 빈·널은 0
        assertThat(repository.deleteByUserSeqList(List.of(user, other))).isEqualTo(3);
        assertThat(queryRepository.findRolesByUserSeqs(List.of(user, other))).isEmpty();
        assertThat(queryRepository.findRoles(ADMIN)).containsExactly(RoleType.ADMIN);
        assertThat(repository.deleteByUserSeqList(List.of(user, other))).as("이미 지운 계정").isZero();
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();

        // FK 없음 — tb_user 행을 지워도 역할 행은 남는다(애플리케이션은 계정 행을 지우지 않고 cascade 가 역할을 지운다)
        repository.grant(UserRole.grant(other, RoleType.USER, SUPER_ADMIN));
        jdbc.update("DELETE FROM tb_user WHERE user_seq = ?", other);
        assertThat(queryRepository.findRoles(other)).containsExactly(RoleType.USER);

        // 이미 있는 (user_seq, role_type) — 시드 행(2 ADMIN). 부여 호출에서 DB PK 위반(jOOQ 예외 변환 → DuplicateKeyException)
        assertThatThrownBy(() -> repository.grant(UserRole.grant(ADMIN, RoleType.ADMIN, SUPER_ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("등록 감사(JdbcAudit) — 시큐리티 사용자 없으면 grant 에 넘긴 등록자, 있으면 그 사용자·등록일은 현재 시각(초 절삭)")
    void registerAudit() {
        int user = insertUser("ur-c@test.com");
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        repository.grant(UserRole.grant(user, RoleType.USER, SUPER_ADMIN));
        loginAs(ADMIN);
        repository.grant(UserRole.grant(user, RoleType.ADMIN, SUPER_ADMIN));

        Map<String, Object> system = jdbc.queryForMap("SELECT register_user_seq, register_date FROM tb_user_role WHERE user_seq = ? AND role_type = 'USER'", user);
        Map<String, Object> byAdmin = jdbc.queryForMap("SELECT register_user_seq, register_date FROM tb_user_role WHERE user_seq = ? AND role_type = 'ADMIN'", user);
        assertThat(system.get("register_user_seq")).as("시스템 작업 = 선언 값").isEqualTo(SUPER_ADMIN);
        assertThat(byAdmin.get("register_user_seq")).as("시큐리티 사용자 우선").isEqualTo(ADMIN);
        Instant registerDate = ((java.sql.Timestamp) byAdmin.get("register_date")).toInstant();
        assertThat(registerDate).isAfterOrEqualTo(before);
        assertThat(registerDate.getNano()).as("초 절삭").isZero();
    }

    @Test
    @DisplayName("jOOQ 생성 코드 role_type → RoleType — 읽기(tb_user_role·tb_user)·쓰기(name() 문자열 저장)")
    void jooqRoleTypeEnum() {
        assertThat(dsl.select(TB_USER_ROLE.ROLE_TYPE).from(TB_USER_ROLE).where(TB_USER_ROLE.USER_SEQ.eq(SUPER_ADMIN)).fetchOne(TB_USER_ROLE.ROLE_TYPE))
                .isEqualTo(RoleType.SUPER_ADMIN);

        int user = insertUser("ur-d@test.com");
        dsl.insertInto(TB_USER_ROLE)
                .set(TB_USER_ROLE.USER_SEQ, user)
                .set(TB_USER_ROLE.ROLE_TYPE, RoleType.USER)
                .set(TB_USER_ROLE.REGISTER_USER_SEQ, SUPER_ADMIN)
                .execute();
        assertThat(jdbc.queryForObject("SELECT role_type FROM tb_user_role WHERE user_seq = ?", String.class, user)).isEqualTo("USER");
        assertThat(queryRepository.findRoles(user)).as("JPA @Enumerated(STRING) 과 같은 값").containsExactly(RoleType.USER);
    }
}
