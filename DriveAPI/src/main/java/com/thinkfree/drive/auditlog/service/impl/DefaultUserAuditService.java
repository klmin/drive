package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.auditlog.support.UserAuditSupport;
import com.thinkfree.drive.auditlog.support.UserInfoUpdateAuditSupport;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultUserAuditService implements UserAuditService {

    private final UserInfoUpdateAuditSupport userInfoUpdateAuditSupport;

    @Override
    public void record(EventName eventName, UserIdentity user) {
        UserAuditSupport.record(eventName, user);
    }

    @Override
    public void record(EventName eventName, UserIdentity user, String beforeValue, String afterValue) {
        UserAuditSupport.record(eventName, user, beforeValue, afterValue);
    }

    @Override
    public void recordSelfActed(EventName eventName, UserIdentity user) {
        UserAuditSupport.recordSelfActed(eventName, user, null, null);
    }

    @Override
    public void record(EventName eventName, UserPendingDeleteData target, UserIdentity actor, Integer tenantSeq) {
        UserAuditSupport.record(eventName, target, actor, tenantSeq);
    }

    @Override
    public void record(EventName eventName, Long objectSeq, String objectUserId, String objectName) {
        UserAuditSupport.record(eventName, objectSeq, objectUserId, objectName);
    }

    @Override
    public void recordInfoUpdate(UserDetailData before, String newName, Long newStorageQuota, String newCountryCode, String newPhone) {
        userInfoUpdateAuditSupport.record(before, newName, newStorageQuota, newCountryCode, newPhone);
    }

    @Override
    public void recordSelfInfoUpdate(UserDetailData before, String newCountryCode, String newPhone) {
        userInfoUpdateAuditSupport.recordSelfInfoUpdate(before, newCountryCode, newPhone);
    }
}
