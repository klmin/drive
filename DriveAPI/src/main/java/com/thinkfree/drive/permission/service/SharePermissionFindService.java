package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.permission.converter.SharePermissionConverter;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.model.ResourcePermission;
import com.thinkfree.drive.permission.repository.SharePermissionQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharePermissionFindService {
    
    private final SharePermissionConverter sharePermissionConverter;
    private final SharePermissionQueryRepository sharePermissionQueryRepository;
    
    public ResourcePermission findResourcePermission(Long shareSeq, int userSeq, int requestUserSeq, Boolean isDelete){
        var query = sharePermissionConverter.toSharePermissionQuery(shareSeq, userSeq, requestUserSeq, isDelete);
        var data = sharePermissionQueryRepository.findSharePermission(query);
        DriveAssert.throwIfNull(data, PermissionErrorCode.RESOURCE_NOT_SHARED);
        return ResourcePermission.from(data, requestUserSeq);
    }
    
}
