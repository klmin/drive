package com.thinkfree.drive.user.admin.service;

import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.query.UserPendingDeleteListQuery;
import com.thinkfree.drive.user.query.UserPendingDeleteTargetQuery;

import java.util.List;

public interface AdminUserFindService {
    
    List<UserPendingDeleteData> findPendingDeleteUsers(UserPendingDeleteListQuery query);
    List<UserPendingDeleteData> findPendingDeleteTargets(UserPendingDeleteTargetQuery query);
}
