package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다중 role 공유 타입 — {@link UserDetailDto#roles}(보유 역할, 비면 동작 역할 1개)·{@link CustomUserDetails#getRoles}/{@code holds},
 * {@link RoleType#isExclusive}. 기존 생성자(6·7인자)는 동작 역할 = 보유 역할 1개로 남는다.
 */
@DisplayName("UserDetailDto.roles / CustomUserDetails.holds / RoleType.isExclusive")
class UserDetailRolesTest {

    @Test
    @DisplayName("기존 6·7인자 생성자 — 보유 역할은 동작 역할 1개, authorities 는 동작 역할(+scope) 그대로")
    void legacyConstructorsFillActingRole() {
        var internal = new CustomUserDetails(new UserDetailDto(10, "u", "n", RoleType.ADMIN.getAuthority(), AuthSource.INTERNAL, 1));
        assertThat(internal.getRoles()).containsExactly(RoleType.ADMIN);
        assertThat(internal.holds(RoleType.ADMIN)).isTrue();
        assertThat(internal.holds(RoleType.USER)).isFalse();
        assertThat(internal.getRoleType()).isEqualTo("ROLE_ADMIN");
        assertThat(internal.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_ADMIN");

        var external = new CustomUserDetails(new UserDetailDto(10, "u", "n", RoleType.USER.getAuthority(), AuthSource.EXTERNAL, 1, Set.of(ApiKeyScope.READ)));
        assertThat(external.getRoles()).containsExactly(RoleType.USER);
        assertThat(external.getAuthorities()).extracting(a -> a.getAuthority()).containsExactlyInAnyOrder("ROLE_USER", ApiKeyScope.READ.authority());
    }

    @Test
    @DisplayName("보유 역할 전부 — 동작 역할과 별개로 실리고 불변, authorities 는 동작 역할(첫 번째) + 나머지 보유 역할")
    void explicitRoles() {
        Set<RoleType> held = new HashSet<>(Set.of(RoleType.USER, RoleType.ADMIN));
        var details = new CustomUserDetails(new UserDetailDto(10, "u", "n", RoleType.USER.getAuthority(), AuthSource.INTERNAL, 1, null, held));
        held.clear();

        assertThat(details.getRoles()).as("방어 복사").containsExactlyInAnyOrder(RoleType.USER, RoleType.ADMIN);
        assertThat(details.holds(RoleType.ADMIN)).isTrue();
        assertThat(details.getRoleType()).isEqualTo("ROLE_USER");
        assertThat(details.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThatThrownBy(() -> details.getRoles().add(RoleType.SUPER_ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("빈 보유 역할·알 수 없는 동작 역할 — 빈 Set 이면 동작 역할로, authority 를 모르면 빈 Set")
    void emptyOrUnknown() {
        assertThat(new UserDetailDto(10, "u", "n", "ROLE_ANONYMOUS", AuthSource.INTERNAL, 1, null, EnumSet.noneOf(RoleType.class)).roles())
                .containsExactly(RoleType.ANONYMOUS);
        assertThat(new UserDetailDto(10, "u", "n", "ROLE_NOPE", AuthSource.INTERNAL, 1).roles()).isEmpty();
        assertThat(new UserDetailDto(10, "u", "n", null, AuthSource.INTERNAL, 1).roles()).isEmpty();
    }

    @Test
    @DisplayName("단독 역할 = SUPER_ADMIN·ANONYMOUS, 부여 가능 = USER·ADMIN")
    void exclusiveRoles() {
        assertThat(EnumSet.allOf(RoleType.class)).filteredOn(RoleType::isExclusive).containsExactly(RoleType.SUPER_ADMIN, RoleType.ANONYMOUS);
        assertThat(EnumSet.allOf(RoleType.class)).filteredOn(RoleType::isRegistrable).containsExactly(RoleType.ADMIN, RoleType.USER);
    }
}
