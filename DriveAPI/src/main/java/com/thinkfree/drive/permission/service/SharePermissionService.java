package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.permission.enums.SharePermissionType;

public interface SharePermissionService {
    
    void authorize(SharePermissionType type, Long shareSeq, int userSeq, int requestUserSeq);
}
