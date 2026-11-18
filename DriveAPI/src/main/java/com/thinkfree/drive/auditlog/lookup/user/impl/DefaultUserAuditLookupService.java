package com.thinkfree.drive.auditlog.lookup.user.impl;

import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultUserAuditLookupService implements UserAuditLookupService {

    private final UserFindService userFindService;

    @Override
    public UserDetailData findUser(Integer userSeq) {
        return userFindService.findDetailByUserSeq(userSeq);
    }
}
