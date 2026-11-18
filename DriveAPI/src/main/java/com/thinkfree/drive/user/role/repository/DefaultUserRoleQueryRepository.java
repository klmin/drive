package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * tb_user_role 읽기 — JPQL 스칼라·튜플(§1 v2 "JPA 우선"). 엔티티를 영속 컨텍스트에 올리지 않으므로 detach 가 필요 없다. 쓰기는 {@link DefaultUserRoleRepository}.
 * 반환 Set 은 {@link EnumSet}(enum 선언 순서 — SUPER_ADMIN, ADMIN, USER, ANONYMOUS).
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserRoleQueryRepository implements UserRoleQueryRepository {

    private final UserRoleJpaRepository jpaRepository;

    @Override
    public Set<RoleType> findRoles(Integer userSeq) {
        Set<RoleType> roles = EnumSet.noneOf(RoleType.class);
        roles.addAll(jpaRepository.findRoleTypesByUserSeq(userSeq));
        return roles;
    }

    @Override
    public boolean holds(Integer userSeq, RoleType roleType) {
        return jpaRepository.countByUserSeqAndRoleType(userSeq, roleType) > 0;
    }

    @Override
    public Map<Integer, Set<RoleType>> findRolesByUserSeqs(Collection<Integer> userSeqs) {
        if (userSeqs == null || userSeqs.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Set<RoleType>> rolesByUser = new HashMap<>();
        for (Object[] row : jpaRepository.findRoleRowsByUserSeqs(userSeqs)) {
            rolesByUser.computeIfAbsent((Integer) row[0], k -> EnumSet.noneOf(RoleType.class)).add((RoleType) row[1]);
        }
        return rolesByUser;
    }
}
