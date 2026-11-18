package com.thinkfree.drive.user.role.dto;

import com.thinkfree.drive.user.enums.RoleType;

import java.util.Set;

/**
 * 계정 한 명의 역할 부여 입력.
 *
 * @param userSeq         대상 계정
 * @param roles           부여할 역할 전부
 * @param registerUserSeq 부여자 — 시큐리티 사용자가 있으면 그 사용자로 덮어쓴다(시스템 작업일 때만 이 값). 새 계정이면 tb_user 등록자와 같은 값
 */
public record UserRoleAssignmentDto(Integer userSeq, Set<RoleType> roles, Integer registerUserSeq) {

    /** 역할 1개로 만드는 계정(OIDC 가입 USER 등) */
    public static UserRoleAssignmentDto single(Integer userSeq, RoleType role, Integer registerUserSeq) {
        return new UserRoleAssignmentDto(userSeq, role == null ? Set.of() : Set.of(role), registerUserSeq);
    }
}
