package com.thinkfree.drive.api.v1.user.usecase;

import com.thinkfree.drive.api.v1.user.converter.UserResponseConverter;
import com.thinkfree.drive.api.v1.user.response.UserResponse;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserUseCase 단위 테스트")
class UserUseCaseTest {

    private static final int USER = 10;

    @Mock
    private UserResponseConverter userResponseConverter;

    @Mock
    private UserFindService userFindService;

    @Mock
    private UserService userService;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private UserUseCase userUseCase;

    @Nested
    @DisplayName("getCurrentUser() - 본인 비밀번호 변경 가능 여부")
    class GetCurrentUser {

        private void currentUser() {
            UserDetailWithRolesData user = mock(UserDetailWithRolesData.class);
            given(user.getUserSeq()).willReturn(USER);
            given(user.getRoles()).willReturn(List.of(RoleType.USER, RoleType.ADMIN));
            given(userService.getCurrentUser(USER)).willReturn(user);
        }

        @Test
        @DisplayName("변경 가능(일반 계정·SELF_MANAGED 보호 계정) → isSelfPasswordChangeable=true")
        void changeable_true() {
            currentUser();
            given(tenantPolicy.isSelfPasswordChangeable(USER)).willReturn(true);

            UserResponse response = userUseCase.getCurrentUser(USER);

            assertThat(response.getIsSelfPasswordChangeable()).isTrue();
            assertThat(response.getUserSeq()).isEqualTo(USER);
            assertThat(response.getRoles()).containsExactly(RoleType.USER, RoleType.ADMIN);
        }

        @Test
        @DisplayName("변경 불가(SYSTEM_MANAGED 보호 계정) → isSelfPasswordChangeable=false")
        void notChangeable_false() {
            currentUser();
            given(tenantPolicy.isSelfPasswordChangeable(USER)).willReturn(false);

            assertThat(userUseCase.getCurrentUser(USER).getIsSelfPasswordChangeable()).isFalse();
        }
    }
}
