package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.converter.ApiKeyConverter;
import com.thinkfree.drive.apikey.dto.ApiKeyIssueDto;
import com.thinkfree.drive.apikey.dto.ApiKeySaveDto;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 발급 요청의 대상 계정을 확인하고 키에 담을 값을 결정한다. role 별 유스케이스 3개가 공통으로 쓴다.
 * <p>
 * 키의 role·tenantSeq 는 서버가 정한다 — role 은 발급 경로(로그인 역할)와 대상의 보유 역할로, 요청 roleType 은 슈퍼관리자가 보유 역할 중 하나를 고를 때만 쓴다.
 * 임의 값을 그대로 받으면 남의 자격 키를 만들 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyIssuer {

    private final UserFindService userFindService;
    private final UserRoleFindService userRoleFindService;
    private final TenantFindService tenantFindService;
    private final ApiKeyConverter apiKeyConverter;

    /**
     * 검증 3단계를 한 번에 수행한다.
     * 유스케이스별 분리 시 검증 누락으로 인한 보안 취약점 방지.
     * <p>
     * 테넌트 검증이 대상 계정의 role 에 달려 있어 대상 조회가 먼저다.
     */
    public ApiKeySaveDto resolve(ApiKeyIssueDto dto) {
        UserDetailData target = resolveTarget(dto);
        RoleType keyRole = resolveKeyRole(dto, target);
        Integer tenantSeq = resolveTenantSeq(dto, target, keyRole);
        String scope = resolveScope(dto.getScopes());
        return apiKeyConverter.toApiKeySaveDto(dto, tenantSeq, target, scope, keyRole);
    }

    /** 키의 actor 가 될 대상 계정을 조회한다. 미지정이면 로그인 사용자 본인 */
    UserDetailData resolveTarget(ApiKeyIssueDto dto) {
        Integer targetUserSeq = Objects.requireNonNullElse(dto.getTargetUserSeq(), dto.getLoginUserSeq());
        return findTarget(dto, targetUserSeq);
    }

    /**
     * 키 역할 — 발급 경로(로그인 역할)로 정하고, 대상이 그 역할을 보유하는지 확인한다(tb_user_role).
     * <ul>
     *   <li>사용자: 본인만, USER 키</li>
     *   <li>관리자: 본인이면 ADMIN 키, 다른 계정이면 USER 키만(대상이 USER 보유 — 관리자 전용 계정은 같은 권한이라 거부)</li>
     *   <li>슈퍼관리자: 본인이면 SUPER_ADMIN 키, 다른 계정이면 요청 roleType(USER·ADMIN 중 대상 보유 역할).
     *       생략하면 대상 역할이 1개일 때 그 역할, 2개면 API_KEY_ROLE_REQUIRED</li>
     * </ul>
     * 같은 권한끼리 대신 발급하면 누가 쓰는 키인지 구분되지 않아 거부한다(API_KEY_TARGET_MUST_BE_SELF).
     */
    RoleType resolveKeyRole(ApiKeyIssueDto dto, UserDetailData target) {
        boolean self = Objects.equals(target.getUserSeq(), dto.getLoginUserSeq());
        if (self) {
            return dto.getLoginRoleType();
        }

        Set<RoleType> held = userRoleFindService.findRoles(target.getUserSeq());
        return switch (dto.getLoginRoleType()) {
            case ADMIN -> {
                DriveAssert.throwIfFalse(held.contains(RoleType.USER), ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);
                yield RoleType.USER;
            }
            case SUPER_ADMIN -> resolveSuperAdminIssuedRole(dto.getRequestedRoleType(), held);
            default -> throw ApiRuntimeException.type(ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);
        };
    }

    // 다른 슈퍼관리자(단독 역할) 대상은 같은 권한이라 거부, 그 외는 보유 역할 중에서
    private static RoleType resolveSuperAdminIssuedRole(RoleType requested, Set<RoleType> held) {
        DriveAssert.throwIfTrue(held.contains(RoleType.SUPER_ADMIN), ApiKeyErrorCode.API_KEY_TARGET_MUST_BE_SELF);
        Set<RoleType> assignable = EnumSet.noneOf(RoleType.class);
        held.stream().filter(RoleType::isRegistrable).forEach(assignable::add);

        if (requested == null) {
            DriveAssert.throwIfTrue(assignable.size() > 1, ApiKeyErrorCode.API_KEY_ROLE_REQUIRED);
            DriveAssert.throwIfTrue(assignable.isEmpty(), ApiKeyErrorCode.API_KEY_ROLE_NOT_HELD);
            return assignable.iterator().next();
        }
        DriveAssert.throwIfFalse(assignable.contains(requested), ApiKeyErrorCode.API_KEY_ROLE_NOT_HELD);
        return requested;
    }

    /**
     * 테넌트 밖 계정을 대상으로 삼을 수 있는 건 슈퍼관리자뿐이다 — 그 경우의 테넌트 대조는 {@link #resolveTenantSeq} 가 한다.
     * <p>
     * 나머지는 자기 테넌트로 좁혀 조회한다. 밖의 계정이면 {@code USER_NOT_FOUND} 라 "없는 계정" 과 응답이 같다 —
     * userSeq 를 훑어 다른 테넌트의 계정 존재 여부를 알아내지 못하게 한다.
     */
    private UserDetailData findTarget(ApiKeyIssueDto dto, Integer targetUserSeq) {
        if (RoleType.SUPER_ADMIN == dto.getLoginRoleType()) {
            return userFindService.findActiveDetailByUserSeq(targetUserSeq);
        }
        return userFindService.findActiveDetailInTenant(targetUserSeq, dto.getLoginTenantSeq());
    }

    /**
     * 키가 속할 테넌트 — 슈퍼관리자만 지정할 수 있고, 나머지는 로그인 사용자의 테넌트를 쓴다.
     * {@code TenantContext}(Host 기준)를 쓰지 않는 이유 — dev fallback 으로 로그인 사용자와 다른 테넌트로 세팅될 수 있음.
     * <p>
     * 슈퍼관리자 대상 키는 소속 테넌트가 없어 null 이고, 그 외에는 대상 계정의 테넌트와 일치해야 한다.
     * 일치 검사를 빼면 A사 키로 B사 사용자 자격 인증이 된다.
     */
    Integer resolveTenantSeq(ApiKeyIssueDto dto, UserDetailData target, RoleType keyRole) {
        if (RoleType.SUPER_ADMIN != dto.getLoginRoleType()) {
            return dto.getLoginTenantSeq();
        }

        if (RoleType.SUPER_ADMIN == keyRole) {
            DriveAssert.throwIfFalse(dto.getTenantSeq() == null, ApiKeyErrorCode.API_KEY_TENANT_NOT_ALLOWED);
            return null;
        }

        DriveAssert.throwIfNull(dto.getTenantSeq(), ApiKeyErrorCode.API_KEY_TENANT_REQUIRED);
        // 미존재 시 예외 — 어느 호스트로도 인증되지 않는 키가 생긴다
        tenantFindService.findByTenantSeq(dto.getTenantSeq());
        DriveAssert.throwIfFalse(Objects.equals(target.getTenantSeq(), dto.getTenantSeq()),
                ApiKeyErrorCode.API_KEY_TENANT_MISMATCH);

        return dto.getTenantSeq();
    }

    /**
     * 요청 scope 를 검증해 DB 저장 형식으로 옮긴다. role 과 무관하게 모든 키가 scope 를 갖는다 —
     * 관리자·슈퍼관리자 키는 검사 대상이 아니지만 값은 채운다(NULL 로 인한 사고 방지).
     * <p>
     * 값을 보정하지 않고 검증만 한다 — 쓰기를 고르면 읽기도 함께 보내는 건 요청 쪽 책임이다.
     * 서버가 채워주면 요청과 저장값이 달라져 무엇이 부여됐는지 호출자가 알 수 없다.
     *
     * @return 공백 구분 문자열
     */
    String resolveScope(List<String> requested) {
        DriveAssert.throwIfTrue(isEmpty(requested), ApiKeyErrorCode.API_KEY_SCOPE_REQUIRED);

        Set<ApiKeyScope> scopes = EnumSet.noneOf(ApiKeyScope.class);
        for (String value : requested) {
            ApiKeyScope scope = ApiKeyScope.from(value);
            DriveAssert.throwIfNull(scope, ApiKeyErrorCode.API_KEY_SCOPE_INVALID);
            scopes.add(scope);
        }

        // 값이 하나하나 유효해도 허용 조합(읽기 전용 / 읽기+쓰기)이 아니면 거부
        DriveAssert.throwIfFalse(ApiKeyScope.isAllowedCombination(scopes), ApiKeyErrorCode.API_KEY_SCOPE_INVALID);

        return ApiKeyScope.join(scopes);
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}
