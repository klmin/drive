package com.thinkfree.drive.user.role.service;

import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;

import java.util.List;

/**
 * 역할 부여 공통 메서드 — 계정의 tb_user_role 을 쓰는 경로는 전부 여기를 거친다(단건 등록·테넌트 생성·CSV·일괄 등록·OIDC 가입).
 * 불변식 검사가 한 곳에 모이도록 호출부가 저장소({@code UserRoleRepository})를 직접 부르지 않는다.
 */
public interface UserRoleService {

    /**
     * 새 계정의 최초 역할 부여 — 계정 INSERT 직후 같은 트랜잭션에서 부른다.
     * 역할 0개·부여 불가 역할(SUPER_ADMIN·ANONYMOUS)은 {@code USER_013}(INSERT_FAIL). 용량 규칙은 호출부의 {@code TenantQuotaPolicy} 몫.
     */
    void assignInitialRoles(UserRoleAssignmentDto assignment);

    /** 여러 계정의 최초 역할 부여(CSV·일괄 등록) — 하나라도 불변식 위반이면 아무것도 쓰지 않고 거부. 빈/널 목록은 아무것도 안 함 */
    void assignInitialRolesAll(List<UserRoleAssignmentDto> assignments);

    /**
     * 기존 계정의 역할 교체 — 현재 보유 역할과의 차이만 부여·회수한다. 호출부는 대상 사용자 행을 잠근 뒤 부른다.
     * <ul>
     *   <li>요청 역할 0개·부여 불가 역할 → {@code USER_028}(INVALID_ROLE_PARAM)</li>
     *   <li>대상이 단독 역할(SUPER_ADMIN·ANONYMOUS) 보유 또는 역할 없음 → {@code USER_005}(USER_NOT_FOUND) — 호출부가 먼저 판정하는 방어 검사</li>
     *   <li>본인 ADMIN 회수 → {@code USER_031}, 사용량 &gt; 0 인 계정의 USER 회수 → {@code USER_030}</li>
     * </ul>
     * 용량 확정(USER 회수 시 0)은 호출부의 {@code TenantQuotaPolicy} 몫.
     */
    void replaceRoles(UserRoleChangeDto change);

    /**
     * 계정 완전 삭제(DELETED) cascade — 대상 계정의 역할 행 전부 삭제. 불변식 검사 없음(계정이 사라지므로 역할 0개가 맞다).
     * 삭제 대기(PENDING_DELETE)는 복원(→ INACTIVE)될 수 있어 부르지 않는다. 역할로 정하는 감사 이벤트는 호출부가 이 호출 전에 읽어 둔다.
     */
    void deleteByUsers(List<Integer> userSeqList);
}
