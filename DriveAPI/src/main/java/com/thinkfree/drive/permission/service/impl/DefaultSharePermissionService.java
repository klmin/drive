package com.thinkfree.drive.permission.service.impl;

import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.SharePermissionFindService;
import com.thinkfree.drive.permission.service.SharePermissionService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultSharePermissionService implements SharePermissionService {
    
    private final SharePermissionFindService sharePermissionFindService;
    
    @Override
    public void authorize(SharePermissionType sharePermissionType, Long shareSeq, int userSeq, int requestUserSeq) {
        
        if(userSeq == requestUserSeq){
            return;
        }
        
        var resourcePermission = sharePermissionFindService.findResourcePermission(shareSeq, userSeq, requestUserSeq, false);
        
        var isShare = sharePermissionType.has(resourcePermission.getPermissions());
        DriveAssert.throwIfFalse(isShare, PermissionErrorCode.ACCESS_DENIED);
        
    }
}
