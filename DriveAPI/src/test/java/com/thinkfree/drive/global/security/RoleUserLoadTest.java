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
 * MCP(API Key·OAuth) 경로의 사용자 로드 — 역할 1개({@code loadUserByUserSeq(seq, role, authSource)}, OAuth 는 토큰 role 클레임). tb_user.role_type 이 아니라 tb_user_role 기준인지,
 * 역할 회수·비활성이면 UsernameNotFoundException 인지 확인. PostgreSQL Testcontainers, 롤백.
 */
@DisplayName("CustomUserDetailsService — 키 역할 1개 로드")
class RoleUserLoadTest extends ServiceTest {

    private static final int TENANT = 1;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    @Autowired
    private JdbcTemplate jdbc;

    private int insert(String userId, RoleType role) {
        return insertUserWithRole(User.createUser(userId, TENANT, null, SignupType.LOCAL, userId, UserStatus.ACTIVE, 0L, 1, null, null, null), role).getUserSeq();
    }

    private void revoke(int userSeq, RoleType role) {
        jdbc.update("DELETE FROM tb_user_role WHERE user_seq = ? AND role_type = ?", userSeq, role.name());
    }

    @Test
    @DisplayName("키 역할 1개 — ADMIN+USER 계정의 USER 키는 USER 만·EXTERNAL, 그 역할 회수 후 UsernameNotFoundException")
    void keyRole() {
        int user = insert("rl-d@test.com", RoleType.ADMIN);
        userRoleRepository.grant(UserRole.grant(user, RoleType.USER, 1));

        CustomUserDetails asUserKey = (CustomUserDetails) customUserDetailsService.loadUserByUserSeq(user, RoleType.USER, AuthSource.EXTERNAL);

        assertThat(asUserKey.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        assertThat(asUserKey.getRoles()).containsExactly(RoleType.USER);
        assertThat(asUserKey.getAuthSource()).isEqualTo(AuthSource.EXTERNAL);

        revoke(user, RoleType.USER);
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUserSeq(user, RoleType.USER, AuthSource.EXTERNAL))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
