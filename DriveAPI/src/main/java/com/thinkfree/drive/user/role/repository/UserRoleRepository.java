package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.entity.UserRole;

import java.util.List;

/**
 * tb_user_role 쓰기 — 역할 부여(INSERT)·회수(DELETE). 읽기는 {@link UserRoleQueryRepository}.
 * 불변식 검사는 호출부(역할 부여 공통 메서드) 몫 — 이미 있는 (user_seq, role_type) 부여는 PK 위반({@code DataIntegrityViolationException}).
 */
public interface UserRoleRepository {

    void grant(UserRole userRole);

    /** 여러 행 부여(일괄 등록·CSV). 빈/널 목록은 SQL 없이 끝 */
    void grantAll(List<UserRole> userRoles);

    /** 회수 — 지운 행 수(없던 역할이면 0) */
    int revoke(Integer userSeq, RoleType roleType);

    /** 계정 완전 삭제 cascade — 대상 계정의 역할 행 전부 삭제, 지운 행 수. 빈/널 목록은 SQL 없이 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);
}
