package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.api.v1.admin.systemconfig.usecase.AdminSystemConfigUseCase;
import com.thinkfree.drive.api.v1.user.controller.UserController;
import com.thinkfree.drive.application.admin.user.AdminUserQueryUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.user.pendingdelete.AdminPendingDeleteUserUseCase;
import com.thinkfree.drive.application.share.ShareTargetUseCase;
import com.thinkfree.drive.application.share.ShareUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SYSTEM_MANAGED(+SINGLE) 에서 쓸 데가 없어 막는 조회·계정 관리 — 공유 조회는 {@link TenantFeature#SHARE}, 재설정 링크 토큰 확인은
 * {@link TenantFeature#MAIL}, 사용자 MFA 초기화는 {@link TenantFeature#MFA_SETTING}, 일괄 등록 전 상태 확인은 {@link TenantFeature#USER_REGISTER},
 * 활성화·삭제 대기 복구·영구 삭제는 {@link TenantFeature#SUB_ACCOUNT_MANAGE}. UseCase(없으면 컨트롤러)에서 판정해 MCP 도 같이 막힌다.
 */
@DisplayName("SYSTEM_MANAGED 차단 조회·계정 관리 — @TenantFeatureRequired")
class SystemManagedReadAndAccountFeatureRequiredTest {

    static Stream<Arguments> methods() {
        return Stream.of(
                Arguments.of(ShareTargetUseCase.class, "findAllPaginated", TenantFeature.SHARE),
                Arguments.of(ShareUseCase.class, "detail", TenantFeature.SHARE),
                Arguments.of(ShareUseCase.class, "findShareUsers", TenantFeature.SHARE),
                Arguments.of(ShareUseCase.class, "searchUsers", TenantFeature.SHARE),
                Arguments.of(ShareUseCase.class, "availableShareTypes", TenantFeature.SHARE),
                Arguments.of(AdminSystemConfigUseCase.class, "existsShare", TenantFeature.SHARE),
                Arguments.of(UserController.class, "verifyPasswordResetToken", TenantFeature.MAIL),
                Arguments.of(AdminUserUseCase.class, "resetMfa", TenantFeature.MFA_SETTING),
                Arguments.of(AdminUserQueryUseCase.class, "findUserStatusByUserIds", TenantFeature.USER_REGISTER),
                Arguments.of(AdminUserUseCase.class, "activateUser", TenantFeature.SUB_ACCOUNT_MANAGE),
                Arguments.of(AdminPendingDeleteUserUseCase.class, "restore", TenantFeature.SUB_ACCOUNT_MANAGE),
                Arguments.of(AdminPendingDeleteUserUseCase.class, "permanentDeleteUsers", TenantFeature.SUB_ACCOUNT_MANAGE));
    }

    @ParameterizedTest(name = "{0}.{1} → {2}")
    @MethodSource("methods")
    @DisplayName("판정 메서드에 해당 기능")
    void annotated(Class<?> type, String name, TenantFeature feature) {
        List<Method> found = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).toList();
        assertThat(found).as("%s.%s 는 public 메서드 하나", type.getSimpleName(), name).hasSize(1);

        TenantFeatureRequired required = found.get(0).getAnnotation(TenantFeatureRequired.class);
        assertThat(required).as(name).isNotNull();
        assertThat(required.value()).isEqualTo(feature);
    }
}
