package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.support.RequestActor;

import java.util.List;


public interface ResourcePermissionService {

    void checkPermission(SharePermissionType type, Long resourceSeq, int ownerUserSeq, RequestActor actor, Boolean isDelete);
    void authorize(SharePermissionType type, Long resourceSeq, int ownerUserSeq, RequestActor actor, Boolean isDelete);
    void checkPermissions(SharePermissionType type, List<PartitionKey> keys, RequestActor actor, Boolean isDelete);

}
