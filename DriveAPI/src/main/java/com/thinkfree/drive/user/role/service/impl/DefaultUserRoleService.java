package com.thinkfree.drive.user.role.service.impl;

import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.entity.UserRole;
import com.thinkfree.drive.user.role.repository.UserRoleQueryRepository;
import com.thinkfree.drive.user.role.repository.UserRoleRepository;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 역할 부여 공통 메서드 — 불변식(ROLE_DESIGN_FINAL §3-1) 중 계정 생성 시점에 해당하는 것을 검사하고 tb_user_role 에 부여한다.
 * <ul>
 *   <li>역할 0개 금지(3), 부여 가능 = USER·ADMIN(1) → 단독 역할 SUPER_ADMIN·ANONYMOUS 는 API 로 부여 불가(2, 시드로만 존재)</li>
 *   <li>역할별 용량(5·7)은 호출부가 {@code TenantQuotaPolicy} 로 확정한 뒤 계정을 INSERT 한다 — 여기서 다시 보지 않는다</li>
 *   <li>기존 계정의 교체({@link #replaceRoles})는 회수 규칙도 본다 — 본인 ADMIN 회수 금지(4), USER 회수는 사용량 0 일 때만(6)</li>
 * </ul>
 * 계정 단위 검사를 더할 자리는 {@link #validate} — 예: SYSTEM_MANAGED 테넌트의 보호 사용자 역할 변경 거부(PLAYGROUND_TENANT_HANDOFF).
 * 부여·회수는 jOOQ 라 호출 즉시 DB 에 나간다 — 같은 트랜잭션의 이어지는 읽기에 바로 보인다(flush 불필요).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultUserRoleService implements UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRoleQueryRepository userRoleQueryRepository;
    private final TenantPolicy tenantPolicy;

    @Override
    public void assignInitialRoles(UserRoleAssignmentDto assignment) {
        validate(assignment);
        userRoleRepository.grantAll(toEntities(assignment));
    }

    @Override
    public void assignInitialRolesAll(List<UserRoleAssignmentDto> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        assignments.forEach(this::validate); // 전부 검사한 뒤에 쓴다 — 중간 실패로 일부만 부여되지 않게
        userRoleRepository.grantAll(assignments.stream().flatMap(a -> toEntities(a).stream()).toList());
    }

    @Override
    public void replaceRoles(UserRoleChangeDto change) {
        DriveAssert.throwIfFalse(RoleType.isAssignable(change.roles()), UserErrorCode.INVALID_ROLE_PARAM);

        Set<RoleType> current = userRoleQueryRepository.findRoles(change.userSeq());
        DriveAssert.throwIfFalse(RoleType.isAssignable(current), UserErrorCode.USER_NOT_FOUND);

        Set<RoleType> revoking = EnumSet.copyOf(current);
        revoking.removeAll(change.roles());
        Set<RoleType> granting = EnumSet.copyOf(change.roles());
        granting.removeAll(current);

        // 보호 계정(테넌트 유형 무관)은 역할을 바꿀 수 없다 — 같은 역할을 다시 보내는 수정(bulk 등)은 변경이 아니라 통과
        if (!revoking.isEmpty() || !granting.isEmpty()) {
            tenantPolicy.requireManageableUser(change.userSeq());
        }

        // 관리 기능은 ADMIN 만 쓰므로 본인 ADMIN 회수 금지가 곧 마지막 관리자 보호다(카운트 불필요)
        DriveAssert.throwIfTrue(revoking.contains(RoleType.ADMIN) && change.userSeq().equals(change.actorUserSeq()),
                UserErrorCode.ROLE_REVOKE_SELF_ADMIN);
        // 사용 중인 파일의 처리 정책이 정해지기 전까지는 파일이 없는 계정만 USER 회수
        DriveAssert.throwIfTrue(revoking.contains(RoleType.USER) && change.useQuotaByte() > 0,
                UserErrorCode.ROLE_REVOKE_USER_IN_USE);

        revoking.forEach(role -> userRoleRepository.revoke(change.userSeq(), role));
        userRoleRepository.grantAll(granting.stream()
                .map(role -> UserRole.grant(change.userSeq(), role, change.actorUserSeq()))
                .toList());
    }

    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        userRoleRepository.deleteByUserSeqList(userSeqList);
    }

    private void validate(UserRoleAssignmentDto assignment) {
        Set<RoleType> roles = assignment.roles();
        boolean invalid = assignment.userSeq() == null
                || roles == null || roles.isEmpty()
                || roles.stream().anyMatch(role -> role == null || !role.isRegistrable());
        DriveAssert.throwIfTrue(invalid, UserErrorCode.INSERT_FAIL);
    }

    private static List<UserRole> toEntities(UserRoleAssignmentDto assignment) {
        return assignment.roles().stream()
                .sorted()
                .map(role -> UserRole.grant(assignment.userSeq(), role, assignment.registerUserSeq()))
                .toList();
    }
}
