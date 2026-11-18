package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.converter.ApiKeyConverter;
import com.thinkfree.drive.apikey.dto.ApiKeyIssueDto;
import com.thinkfree.drive.apikey.dto.ApiKeySaveDto;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.READ_SCOPES;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.READ_SCOPE_VALUE;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.WRITE_SCOPES;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.WRITE_SCOPE_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyIssuer 단위 테스트")
class ApiKeyIssuerTest {

    private static final int TENANT = 1;
    private static final int OTHER_TENANT = 2;
    private static final int LOGIN_USER = 10;
    private static final int TARGET_USER = 20;

    @Mock
    private UserFindService userFindService;

    @Mock
    private UserRoleFindService userRoleFindService;

    @Mock
    private TenantFindService tenantFindService;

    @Mock
    private ApiKeyConverter apiKeyConverter;

    @InjectMocks
    private ApiKeyIssuer apiKeyIssuer;

    /** 로그인 사용자 정보만 채운 요청 — 각 테스트가 검증에 필요한 필드만 덧붙인다 */
    private static ApiKeyIssueDto.ApiKeyIssueDtoBuilder issueDto(RoleType loginRoleType) {
        return ApiKeyIssueDto.builder()
                .loginUserSeq(LOGIN_USER)
                .loginRoleType(loginRoleType)
                .loginTenantSeq(TENANT);
    }

    private static UserDetailData targetMock(Integer userSeq) {
        UserDetailData target = Mockito.mock(UserDetailData.class);
        given(target.getUserSeq()).willReturn(userSeq);
        return target;
    }

    private static UserDetailData tenantTarget(Integer tenantSeq) {
        UserDetailData target = Mockito.mock(UserDetailData.class);
        given(target.getTenantSeq()).willReturn(tenantSeq);
        return target;
    }

    /** 대상 계정의 보유 역할(tb_user_role) */
    private void holds(int userSeq, RoleType... roles) {
        given(userRoleFindService.findRoles(userSeq)).willReturn(Set.of(roles));
    }

    private void assertCode(Runnable call, ApiKeyErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiRuntimeException.class)
                .hasFieldOrPropertyWithValue("code", code);
    }

    @Nested
    @DisplayName("resolve() - 저장 확정값 조립")
    class Resolve {

        @Test
        @DisplayName("관리자가 같은 테넌트 사용자에게 발급 → 결정된 테넌트·대상 계정·scope·키 역할(USER)을 컨버터에 넘긴다")
        void admin_targetUser_passesResolvedValuesToConverter() {
            UserDetailData target = targetMock(TARGET_USER);
            given(userFindService.findActiveDetailInTenant(TARGET_USER, TENANT)).willReturn(target);
            holds(TARGET_USER, RoleType.USER);

            ApiKeyIssueDto issueDto = issueDto(RoleType.ADMIN)
                    .targetUserSeq(TARGET_USER)
                    .scopes(WRITE_SCOPES)
                    .build();

            ApiKeySaveDto expected = ApiKeySaveDto.builder().build();
            given(apiKeyConverter.toApiKeySaveDto(issueDto, TENANT, target, WRITE_SCOPE_VALUE, RoleType.USER)).willReturn(expected);

            assertThat(apiKeyIssuer.resolve(issueDto)).isSameAs(expected);
        }

        /** 관리자·슈퍼관리자 키도 scope 를 갖는다 — 검사 대상이 아닐 뿐 값은 사용자 키와 같은 규칙으로 저장된다 */
        @Test
        @DisplayName("관리자가 본인에게 발급 → ADMIN 키, scope 가 그대로 저장된다")
        void admin_targetSelf_storesScope() {
            UserDetailData target = targetMock(LOGIN_USER);
            given(userFindService.findActiveDetailInTenant(LOGIN_USER, TENANT)).willReturn(target);

            ApiKeyIssueDto issueDto = issueDto(RoleType.ADMIN)
                    .scopes(WRITE_SCOPES)
                    .build();

            ApiKeySaveDto expected = ApiKeySaveDto.builder().build();
            given(apiKeyConverter.toApiKeySaveDto(issueDto, TENANT, target, WRITE_SCOPE_VALUE, RoleType.ADMIN)).willReturn(expected);

            assertThat(apiKeyIssuer.resolve(issueDto)).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("resolveTarget() - 대상 계정 조회")
    class ResolveTarget {

        @Test
        @DisplayName("대상 계정 미지정 → 로그인 사용자 본인")
        void targetUserSeqMissing_returnsLoginUser() {
            UserDetailData target = targetMock(LOGIN_USER);
            given(userFindService.findActiveDetailInTenant(LOGIN_USER, TENANT)).willReturn(target);

            assertThat(apiKeyIssuer.resolveTarget(issueDto(RoleType.USER).build()).getUserSeq())
                    .isEqualTo(LOGIN_USER);
        }

        /** 관리자·사용자는 테넌트 범위 조회를 쓴다 — 밖의 계정은 "없는 계정" 과 응답이 같아 존재 여부가 노출되지 않는다 */
        @Test
        @DisplayName("관리자 + 다른 테넌트 계정 지정 → USER_NOT_FOUND 예외")
        void admin_targetInOtherTenant_throwsException() {
            given(userFindService.findActiveDetailInTenant(TARGET_USER, TENANT))
                    .willThrow(new ApiRuntimeException(UserErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> apiKeyIssuer.resolveTarget(issueDto(RoleType.ADMIN).targetUserSeq(TARGET_USER).build()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.USER_NOT_FOUND);
        }

        /** 슈퍼관리자만 테넌트 밖 계정을 대상으로 삼을 수 있다 — 테넌트 대조는 resolveTenantSeq 가 한다 */
        @Test
        @DisplayName("슈퍼관리자 + 다른 테넌트 계정 → 테넌트 무관 조회")
        void superAdmin_findsAcrossTenants() {
            UserDetailData target = targetMock(TARGET_USER);
            given(userFindService.findActiveDetailByUserSeq(TARGET_USER)).willReturn(target);

            assertThat(apiKeyIssuer.resolveTarget(issueDto(RoleType.SUPER_ADMIN).targetUserSeq(TARGET_USER).build()).getUserSeq())
                    .isEqualTo(TARGET_USER);
        }

        @Test
        @DisplayName("비활성 계정 지정 → 조회 단계에서 예외")
        void targetUserInactive_throwsException() {
            given(userFindService.findActiveDetailInTenant(TARGET_USER, TENANT))
                    .willThrow(new ApiRuntimeException(ApiKeyErrorCode.API_KEY_NOT_FOUND));

            assertThatThrownBy(() -> apiKeyIssuer.resolveTarget(issueDto(RoleType.ADMIN).targetUserSeq(TARGET_USER).build()))
                    .isInstanceOf(ApiRuntimeException.class);
        }
    }

    @Nested
    @DisplayName("resolveKeyRole() - 키 역할 = 발급 경로 + 대상 보유 역할")
    class ResolveKeyRole {

        @Test
        @DisplayName("본인 발급 → 로그인 역할 그대로(사용자 USER·관리자 ADMIN·슈퍼관리자 SUPER_ADMIN), 보유 역할 조회 없음")
        void self_usesLoginRole() {
            UserDetailData self = targetMock(LOGIN_USER);
            for (RoleType role : new RoleType[]{RoleType.USER, RoleType.ADMIN, RoleType.SUPER_ADMIN}) {
                assertThat(apiKeyIssuer.resolveKeyRole(issueDto(role).build(), self)).isEqualTo(role);
            }
            Mockito.verifyNoInteractions(userRoleFindService);
        }

        @Test
        @DisplayName("USER+ADMIN 계정이 사용자 화면에서 본인 키 → USER 키(계정 대표 역할과 무관)")
        void userAndAdminAccount_userScreen_userKey() {
            assertThat(apiKeyIssuer.resolveKeyRole(issueDto(RoleType.USER).build(), targetMock(LOGIN_USER)))
                    .isEqualTo(RoleType.USER);
        }

        @Test
        @DisplayName("사용자 + 다른 계정 → API_KEY_TARGET_MUST_BE_SELF")
        void user_otherTarget_rejected() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.USER);
            assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.USER).build(), target), ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);
        }

        @Test
        @DisplayName("관리자 + 다른 계정(USER 보유, ADMIN 도 보유) → USER 키")
        void admin_otherTargetHoldingUser_userKey() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.USER, RoleType.ADMIN);
            assertThat(apiKeyIssuer.resolveKeyRole(issueDto(RoleType.ADMIN).build(), target)).isEqualTo(RoleType.USER);
        }

        @Test
        @DisplayName("관리자 + 관리자 역할만 가진 다른 계정 → API_KEY_TARGET_MUST_BE_SELF")
        void admin_otherAdminOnly_rejected() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.ADMIN);
            assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.ADMIN).build(), target), ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);
        }

        @Test
        @DisplayName("슈퍼관리자 + 역할 1개 계정, roleType 생략 → 그 역할")
        void superAdmin_singleRole_omitted() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.ADMIN);
            assertThat(apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).build(), target)).isEqualTo(RoleType.ADMIN);
        }

        @Test
        @DisplayName("슈퍼관리자 + USER+ADMIN 계정, roleType 생략 → API_KEY_ROLE_REQUIRED, 지정하면 그 역할")
        void superAdmin_twoRoles() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.USER, RoleType.ADMIN);
            assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).build(), target), ApiKeyErrorCode.API_KEY_ROLE_REQUIRED);
            assertThat(apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).requestedRoleType(RoleType.USER).build(), target)).isEqualTo(RoleType.USER);
            assertThat(apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).requestedRoleType(RoleType.ADMIN).build(), target)).isEqualTo(RoleType.ADMIN);
        }

        @Test
        @DisplayName("슈퍼관리자 + 대상이 보유하지 않은 역할·SUPER_ADMIN·ANONYMOUS 지정 → API_KEY_ROLE_NOT_HELD")
        void superAdmin_roleNotHeld() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.USER);
            for (RoleType requested : new RoleType[]{RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.ANONYMOUS}) {
                assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).requestedRoleType(requested).build(), target),
                        ApiKeyErrorCode.API_KEY_ROLE_NOT_HELD);
            }
        }

        @Test
        @DisplayName("슈퍼관리자 + 다른 슈퍼관리자 → API_KEY_TARGET_MUST_BE_SELF, 익명 계정 → API_KEY_ROLE_NOT_HELD")
        void superAdmin_exclusiveTargets() {
            UserDetailData target = targetMock(TARGET_USER);
            holds(TARGET_USER, RoleType.SUPER_ADMIN);
            assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).build(), target), ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);

            holds(TARGET_USER, RoleType.ANONYMOUS);
            assertCode(() -> apiKeyIssuer.resolveKeyRole(issueDto(RoleType.SUPER_ADMIN).build(), target), ApiKeyErrorCode.API_KEY_ROLE_NOT_HELD);
        }
    }

    @Nested
    @DisplayName("resolveTenantSeq() - 키가 속할 테넌트 결정")
    class ResolveTenantSeq {

        @Test
        @DisplayName("관리자 + tenantSeq 지정 → 요청값을 무시하고 로그인 사용자의 테넌트 사용")
        void admin_tenantSeqGiven_usesLoginTenant() {
            ApiKeyIssueDto issueDto = issueDto(RoleType.ADMIN).tenantSeq(OTHER_TENANT).build();

            assertThat(apiKeyIssuer.resolveTenantSeq(issueDto, Mockito.mock(UserDetailData.class), RoleType.USER)).isEqualTo(TENANT);
        }

        @Test
        @DisplayName("슈퍼관리자 키 + tenantSeq 미지정 → null (소속 테넌트가 없는 키)")
        void superAdmin_superAdminKey_returnsNull() {
            assertThat(apiKeyIssuer.resolveTenantSeq(issueDto(RoleType.SUPER_ADMIN).build(), Mockito.mock(UserDetailData.class), RoleType.SUPER_ADMIN)).isNull();
        }

        @Test
        @DisplayName("슈퍼관리자 키 + tenantSeq 지정 → API_KEY_TENANT_NOT_ALLOWED 예외")
        void superAdmin_superAdminKeyWithTenantSeq_throwsException() {
            ApiKeyIssueDto issueDto = issueDto(RoleType.SUPER_ADMIN).tenantSeq(TENANT).build();

            assertCode(() -> apiKeyIssuer.resolveTenantSeq(issueDto, Mockito.mock(UserDetailData.class), RoleType.SUPER_ADMIN),
                    ApiKeyErrorCode.API_KEY_TENANT_NOT_ALLOWED);
        }

        @Test
        @DisplayName("슈퍼관리자 + 사용자 키 + tenantSeq 지정 → 요청값 사용")
        void superAdmin_userKey_usesRequestedTenant() {
            given(tenantFindService.findByTenantSeq(TENANT)).willReturn(Mockito.mock(TenantData.class));
            UserDetailData target = tenantTarget(TENANT);

            assertThat(apiKeyIssuer.resolveTenantSeq(issueDto(RoleType.SUPER_ADMIN).tenantSeq(TENANT).build(), target, RoleType.USER))
                    .isEqualTo(TENANT);
        }

        @Test
        @DisplayName("슈퍼관리자 + 사용자 키 + tenantSeq 미지정 → API_KEY_TENANT_REQUIRED 예외")
        void superAdmin_userKeyWithoutTenantSeq_throwsException() {
            assertCode(() -> apiKeyIssuer.resolveTenantSeq(issueDto(RoleType.SUPER_ADMIN).build(), Mockito.mock(UserDetailData.class), RoleType.USER),
                    ApiKeyErrorCode.API_KEY_TENANT_REQUIRED);
        }

        @Test
        @DisplayName("슈퍼관리자 + 대상 계정이 지정 테넌트에 속하지 않음 → API_KEY_TENANT_MISMATCH 예외")
        void superAdmin_targetNotInGivenTenant_throwsException() {
            given(tenantFindService.findByTenantSeq(TENANT)).willReturn(Mockito.mock(TenantData.class));
            UserDetailData target = tenantTarget(OTHER_TENANT);

            assertCode(() -> apiKeyIssuer.resolveTenantSeq(issueDto(RoleType.SUPER_ADMIN).tenantSeq(TENANT).build(), target, RoleType.ADMIN),
                    ApiKeyErrorCode.API_KEY_TENANT_MISMATCH);
        }
    }

    @Nested
    @DisplayName("resolveScope() - scope 검증 및 저장값 결정")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ResolveScope {

        @Test
        @DisplayName("읽기 전용 조합 → 그대로 저장")
        void readOnlyCombination_returnsStored() {
            assertThat(apiKeyIssuer.resolveScope(READ_SCOPES)).isEqualTo(READ_SCOPE_VALUE);
        }

        @Test
        @DisplayName("읽기+쓰기 조합 → 그대로 저장")
        void writeCombination_returnsStored() {
            assertThat(apiKeyIssuer.resolveScope(WRITE_SCOPES)).isEqualTo(WRITE_SCOPE_VALUE);
        }

        @Test
        @DisplayName("순서 뒤바뀜·중복 → 정렬·중복 제거 후 동일 문자열")
        void unorderedAndDuplicated_returnsSortedValue() {
            assertThat(apiKeyIssuer.resolveScope(List.of("mcp:read", "api:read", "mcp:read")))
                    .isEqualTo(READ_SCOPE_VALUE);
        }

        @Test
        @DisplayName("scope 미지정 → API_KEY_SCOPE_REQUIRED 예외")
        void scopeMissing_throwsException() {
            assertThatThrownBy(() -> apiKeyIssuer.resolveScope(List.of()))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", ApiKeyErrorCode.API_KEY_SCOPE_REQUIRED);
        }

        /**
         * 값은 실재할 법하되 ApiKeyScope 에 추가될 일이 없는 것으로 둔다.
         * 예전에 "mcp:read" 를 썼다가 TFDRIVE-1691 이 그 값을 지원 목록에 넣으면서 테스트가 깨졌다.
         */
        @Test
        @DisplayName("지원하지 않는 scope 지정 → API_KEY_SCOPE_INVALID 예외")
        void unsupportedScope_throwsException() {
            assertThatThrownBy(() -> apiKeyIssuer.resolveScope(List.of("unknown:scope")))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", ApiKeyErrorCode.API_KEY_SCOPE_INVALID);
        }

        /**
         * 값 하나하나는 유효한데 조합이 허용 목록에 없는 경우.
         * 백엔드가 빠진 값을 채워주지 않으므로(요청값 그대로 저장) 여기서 걸러야 한다.
         */
        Stream<Arguments> disallowedCombinations() {
            return Stream.of(
                    Arguments.of(List.of("api:write"), "쓰기만 — 읽기 누락"),
                    Arguments.of(List.of("api:read"), "api 만 — mcp 누락"),
                    Arguments.of(List.of("mcp:read"), "mcp 만 — api 누락"),
                    Arguments.of(List.of("api:read", "api:write", "mcp:read"), "쓰기인데 mcp:write 누락"),
                    Arguments.of(List.of("api:read", "mcp:read", "mcp:write"), "쓰기인데 api:write 누락")
            );
        }

        @ParameterizedTest(name = "[{index}] {1} → API_KEY_SCOPE_INVALID")
        @MethodSource("disallowedCombinations")
        @DisplayName("허용되지 않는 조합 → API_KEY_SCOPE_INVALID 예외")
        void disallowedCombination_throwsException(List<String> scopes, String description) {
            assertThatThrownBy(() -> apiKeyIssuer.resolveScope(scopes))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasFieldOrPropertyWithValue("code", ApiKeyErrorCode.API_KEY_SCOPE_INVALID);
        }
    }
}
