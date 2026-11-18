package com.thinkfree.drive.permission.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.model.ResourcePermission;
import com.thinkfree.drive.permission.service.ResourcePermissionFindService;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultResourcePermissionService implements ResourcePermissionService {

    private final ResourcePermissionFindService resourcePermissionFindService;

    @Override
    public void checkPermission(SharePermissionType type, Long resourceSeq, int ownerUserSeq, RequestActor actor, Boolean isDelete){
        if (ownerUserSeq == actor.userSeq()) {
            return;
        }

        authorize(type, resourceSeq, ownerUserSeq, actor, isDelete);
    }

    @Override
    public void authorize(SharePermissionType type, Long resourceSeq, int ownerUserSeq, RequestActor actor, Boolean isDelete){
        var resourcePermission = resolve(resourceSeq, ownerUserSeq, actor.userSeq(), isDelete);
        verify(type, resourcePermission, actor);
    }

    @Override
    public void checkPermissions(SharePermissionType type, List<PartitionKey> keys, RequestActor actor, Boolean isDelete) {

        var dataList = resourcePermissionFindService.findResourcesPermissionData(actor.userSeq(), keys, isDelete);

        DriveAssert.throwIfEmpty(dataList, PermissionErrorCode.RESOURCE_NOT_SHARED);

        if(keys.size() != dataList.size()) {
            throw ApiRuntimeException.type(PermissionErrorCode.RESOURCE_NOT_SHARED);
        }

        for(var data : dataList){
            verify(type, ResourcePermission.from(data, actor.userSeq()), actor);
        }
    }

    private ResourcePermission resolve(Long resourceSeq, int ownerUserSeq, int requestUserSeq, Boolean isDelete){
        DriveAssert.throwIfNull(resourceSeq, PermissionErrorCode.ACCESS_DENIED);
        var partitionKey = new PartitionKey(ownerUserSeq, resourceSeq);
        return resourcePermissionFindService.findResourcePermission(requestUserSeq, partitionKey, isDelete);
    }

    /**
     * 권한 검증. 익명 여부는 호출부가 구성한 {@link RequestActor} 로 전달받아 판정한다.
     * - 익명(anonymous) 요청이면 전체공개(PUBLIC_LINK) 리소스에 한해서만 허용
     * - 이후 요청 권한 보유 여부 검사
     */
    private void verify(SharePermissionType type, ResourcePermission resourcePermission, RequestActor actor) {
        if (actor.anonymous()) {
            DriveAssert.throwIfFalse(ShareType.PUBLIC_LINK == resourcePermission.getShareType(), PermissionErrorCode.ACCESS_DENIED);
        }
        if (!type.has(resourcePermission.getPermissions())) {
            throw ApiRuntimeException.type(PermissionErrorCode.ACCESS_DENIED);
        }
    }
}
