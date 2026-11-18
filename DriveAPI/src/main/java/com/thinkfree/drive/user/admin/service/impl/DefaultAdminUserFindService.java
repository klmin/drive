package com.thinkfree.drive.user.admin.service.impl;

import com.thinkfree.drive.user.admin.service.AdminUserFindService;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.query.UserPendingDeleteListQuery;
import com.thinkfree.drive.user.query.UserPendingDeleteTargetQuery;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultAdminUserFindService implements AdminUserFindService {
    
    private final UserQueryRepository userQueryRepository;
    
    @Override
    public List<UserPendingDeleteData> findPendingDeleteUsers(UserPendingDeleteListQuery query) {
        return userQueryRepository.findPendingDeleteUsers(query);
    }
    
    @Override
    public List<UserPendingDeleteData> findPendingDeleteTargets(UserPendingDeleteTargetQuery query) {
        return userQueryRepository.findPendingDeleteTargets(query);
    }
}
