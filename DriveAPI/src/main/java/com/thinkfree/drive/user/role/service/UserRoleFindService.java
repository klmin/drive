package com.thinkfree.drive.user.role.service;

import com.thinkfree.drive.user.enums.RoleType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 보유 역할 조회 — 로그인·토큰 발급·재발급이 "이 역할로 들어올 수 있는지" 판정할 때 쓴다. 부여·회수는 {@link UserRoleService}.
 */
public interface UserRoleFindService {

    boolean holds(Integer userSeq, RoleType roleType);

    /** 보유 역할 전부 — 없으면 빈 Set */
    Set<RoleType> findRoles(Integer userSeq);

    /** 여러 사용자의 보유 역할(목록 응답용) — 역할 행이 없는 사용자는 키가 없다. 빈 목록은 SQL 없이 빈 Map */
    Map<Integer, Set<RoleType>> findRolesByUserSeqs(Collection<Integer> userSeqs);
}
