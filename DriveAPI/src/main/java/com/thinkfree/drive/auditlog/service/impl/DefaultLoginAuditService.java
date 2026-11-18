package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auditlog.support.UserAuditSupport;
import com.thinkfree.drive.user.data.UserIdentity;
import org.springframework.stereotype.Service;

@Service
public class DefaultLoginAuditService implements LoginAuditService {

    @Override
    public void recordLoginSuccess(UserIdentity user) {
        UserAuditSupport.recordSelfActed(EventName.LOGIN_SUCCESS, user, null, null);
    }

    @Override
    public void recordLogout(UserIdentity user) {
        UserAuditSupport.recordSelfActed(EventName.LOGOUT, user, null, null);
    }

    @Override
    public void recordLoginFailure(UserIdentity user) {
        UserAuditSupport.recordSelfActed(EventName.LOGIN_FAILED, user, null, null);
    }

    @Override
    public void recordAccountLocked(UserIdentity user) {
        UserAuditSupport.recordAccountLocked(user);
    }

    @Override
    public void recordAccountUnlocked(UserIdentity user) {
        UserAuditSupport.record(EventName.ACCOUNT_LOCKED_STATUS_CHANGE, user, "LOCKED", "UNLOCKED");
    }
}
