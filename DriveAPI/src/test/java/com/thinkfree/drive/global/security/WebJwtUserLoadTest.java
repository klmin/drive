package com.thinkfree.drive.global.security;

import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.role.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 웹 JWT 필터의 요청당 1회 조회({@code CustomUserDetailsService.loadUserByUserSeq(seq, role)} → JPQL 상태·역할 보유·계정 값)
 * — 구 3문장(활성 count·보유 count·PK 조회)과 같은 판정인지, 예외 케이스(없음·비활성·삭제 대기·삭제·역할 회수)는 전부 UsernameNotFoundException.
 * PostgreSQL Testcontainers, 롤백.
 */
@DisplayName("CustomUserDetailsService.loadUserByUserSeq(seq, role) — 활성·역할 보유·로드 1회 조회")
class WebJwtUserLoadTest extends ServiceTest {

    private static final int TENANT = 1;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private JdbcTemplate jdbc;

    private int insert(String userId, RoleType role) {
        return insertUserWithRole(User.createUser(userId, TENANT, null, SignupType.LOCAL, userId, UserStatus.ACTIVE, 0L, 1, null, null, null), role).getUserSeq();
    }

    private void status(int userSeq, UserStatus status) {
        jdbc.update("UPDATE tb_user SET status = ? WHERE user_seq = ?", status.name(), userSeq);
    }

    private CustomUserDetails load(int userSeq, RoleType role) {
        return (CustomUserDetails) customUserDetailsService.loadUserByUserSeq(userSeq, role);
    }

    @Test
    @DisplayName("ACTIVE + 보유 — 계정 값·동작 역할(authority 1개)·보유 역할 {그 역할}·INTERNAL")
    void activeHeld() {
        int user = insert("wl-a@test.com", RoleType.USER);
        userRoleRepository.grant(UserRole.grant(user, RoleType.ADMIN, 1));

        CustomUserDetails asAdmin = load(user, RoleType.ADMIN);
        CustomUserDetails asUser = load(user, RoleType.USER);

        assertThat(asAdmin.getUserSeq()).isEqualTo(user);
        assertThat(asAdmin.getUserId()).isEqualTo("wl-a@test.com");
        assertThat(asAdmin.getTenantSeq()).isEqualTo(TENANT);
        assertThat(asAdmin.getAuthSource()).isEqualTo(AuthSource.INTERNAL);
        assertThat(asAdmin.getRoleType()).isEqualTo("ROLE_ADMIN");
        assertThat(asAdmin.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(asAdmin.getRoles()).containsExactly(RoleType.ADMIN);
        assertThat(asUser.getAuthorities()).extracting(GrantedAuthority::getAuthority).as("같은 계정도 토큰 역할만").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("예외 — 미보유 역할·없는 계정·INACTIVE·PENDING_DELETE·DELETED 는 UsernameNotFoundException")
    void rejected() {
        int user = insert("wl-b@test.com", RoleType.USER);

        assertThatThrownBy(() -> load(user, RoleType.ADMIN)).as("미보유").isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> load(999999, RoleType.USER)).as("없는 계정").isInstanceOf(UsernameNotFoundException.class);
        for (UserStatus s : new UserStatus[]{UserStatus.INACTIVE, UserStatus.PENDING_DELETE, UserStatus.DELETED}) {
            status(user, s);
            assertThatThrownBy(() -> load(user, RoleType.USER)).as(s.name()).isInstanceOf(UsernameNotFoundException.class);
        }
        status(user, UserStatus.ACTIVE);
        assertThat(load(user, RoleType.USER).getUserSeq()).as("ACTIVE 로 돌리면 다시 로드").isEqualTo(user);
    }

    @Test
    @DisplayName("회수 즉시 반영 — 회수 뒤 같은 트랜잭션 다음 조회부터 예외, 다시 부여하면 로드")
    void revokeAndRegrant() {
        int user = insert("wl-c@test.com", RoleType.USER);
        userRoleRepository.grant(UserRole.grant(user, RoleType.ADMIN, 1));
        assertThat(load(user, RoleType.ADMIN).getUserSeq()).isEqualTo(user);

        userRoleRepository.revoke(user, RoleType.ADMIN);
        assertThatThrownBy(() -> load(user, RoleType.ADMIN)).isInstanceOf(UsernameNotFoundException.class);
        assertThat(load(user, RoleType.USER).getUserSeq()).as("남은 역할은 그대로").isEqualTo(user);

        userRoleRepository.grant(UserRole.grant(user, RoleType.ADMIN, 1));
        assertThat(load(user, RoleType.ADMIN).getRoleType()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("시드 계정 — 1 SUPER_ADMIN·2 ADMIN·3 ANONYMOUS 는 자기 역할로만 로드")
    void seedAccounts() {
        assertThat(load(1, RoleType.SUPER_ADMIN).getRoleType()).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(load(2, RoleType.ADMIN).getRoleType()).isEqualTo("ROLE_ADMIN");
        assertThat(load(3, RoleType.ANONYMOUS).isAnonymous()).isTrue();
        assertThatThrownBy(() -> load(2, RoleType.USER)).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> load(1, RoleType.ADMIN)).as("SA 는 ADMIN 행이 없음(하이어라키는 시큐리티 몫)").isInstanceOf(UsernameNotFoundException.class);
    }
}
