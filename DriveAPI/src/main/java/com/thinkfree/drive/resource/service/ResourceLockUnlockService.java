package com.thinkfree.drive.resource.service;

import com.thinkfree.drive.resource.query.ResourceLockExpireQuery;

import java.time.Instant;

public interface ResourceLockUnlockService {

    void lockResource(long resourceSeq, int userSeq, int loginUserSeq, Instant updateDate);
    void unlockResource(long resourceSeq, int userSeq, int loginUserSeq, Instant updateDate);
    void checkLockedResource(long resourceSeq, int userSeq);
    int updateExpiredLocks(ResourceLockExpireQuery query);

}
