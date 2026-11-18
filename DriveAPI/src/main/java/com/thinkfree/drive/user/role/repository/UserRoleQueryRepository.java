package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.user.enums.RoleType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * tb_user_role 읽기 — 보유 역할 조회. 쓰기는 {@link UserRoleRepository}.
 */
public interface UserRoleQueryRepository {

    /** 사용자의 보유 역할 전부 — 없으면 빈 Set */
    Set<RoleType> findRoles(Integer userSeq);

    /** 사용자가 그 역할을 보유하는지 */
    boolean holds(Integer userSeq, RoleType roleType);

    /** 여러 사용자의 보유 역할(목록 응답용) — 역할 행이 없는 사용자는 키가 없다. 빈/널 목록은 SQL 없이 빈 Map */
    Map<Integer, Set<RoleType>> findRolesByUserSeqs(Collection<Integer> userSeqs);
}
