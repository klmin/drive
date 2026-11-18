package com.thinkfree.drive.user.role.service.impl;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.repository.UserRoleQueryRepository;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultUserRoleFindService implements UserRoleFindService {

    private final UserRoleQueryRepository userRoleQueryRepository;

    @Override
    public boolean holds(Integer userSeq, RoleType roleType) {
        return userSeq != null && roleType != null && userRoleQueryRepository.holds(userSeq, roleType);
    }

    @Override
    public Set<RoleType> findRoles(Integer userSeq) {
        return userRoleQueryRepository.findRoles(userSeq);
    }

    @Override
    public Map<Integer, Set<RoleType>> findRolesByUserSeqs(Collection<Integer> userSeqs) {
        return userRoleQueryRepository.findRolesByUserSeqs(userSeqs);
    }
}
