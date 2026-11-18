package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.permission.support.ResourcePermissionAccessor;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Builder(access = AccessLevel.PRIVATE)
@Getter
public class ResourcePermission implements PartitionKeyProvider {
    
    private Integer userSeq;
    private Long resourceSeq;
    private Long shareSeq;
    private Long shareResourceSeq;
    private ShareType shareType;
    private Long shareTargetSeq;
    private Long shareTargetShareResourceSeq;
    private Permissions permissions;
    private Boolean isOwner;
    private Boolean isShared;
    private ResourceLocation resourceLocation;
    private Integer depth;
    
    public static ResourcePermission from(ResourcePermissionAccessor accessor, int requestUserSeq){
        
        int ownerSeq = accessor.getUserSeq();
        Long resourceSeq = accessor.getResourceSeq();
        Long nearestShareSeq = accessor.getShareSeq();
        Long nearestShareResourceSeq = accessor.getShareResourceSeq();
        ShareType nearestShareType = accessor.getShareType();
        Long shareTargetShareResourceSeq = accessor.getShareTargetShareResourceSeq();
        
        boolean isOwner = ownerSeq == requestUserSeq;
        boolean isOwnerResource = isOwner && resourceSeq.equals(nearestShareResourceSeq);
        ResourceLocation resourceLocation = isOwner ? ResourceLocation.MY : ResourceLocation.SHARE;
        
        // 가장 가까운 link 공유에 내 target 이 달려 있으면 target 권한이 우선,
        // 없으면 해당 link 권한을 사용한다. link 공유가 없으면(SPECIFIC 등) target 유무로 판단한다.
        boolean hasLink = nearestShareResourceSeq != null;
        boolean useTarget = hasLink
                ? (nearestShareSeq != null && nearestShareSeq.equals(accessor.getShareTargetShareSeq()))
                : (shareTargetShareResourceSeq != null);

        Long shareResourceSeq = isOwnerResource ? nearestShareResourceSeq : (useTarget ? shareTargetShareResourceSeq : nearestShareResourceSeq);
        Long shareSeq = isOwnerResource ? nearestShareSeq : (resourceSeq.equals(shareResourceSeq) ? (useTarget ? accessor.getShareTargetShareSeq() : nearestShareSeq) : null);
        ShareType shareType = isOwnerResource ? nearestShareType : (useTarget ? accessor.getShareTargetShareType() : nearestShareType);
        Long shareTargetSeq = resourceSeq.equals(shareTargetShareResourceSeq) ? accessor.getShareTargetSeq() : null;
        Integer depth = useTarget ? accessor.getShareTargetDepth() : (hasLink ? accessor.getShareDepth() : null);
        Permissions permissions = isOwner ? Permissions.forOwner() : buildPermissions(accessor, useTarget, hasLink);
      
        boolean isShared = shareResourceSeq != null;
        
        return ResourcePermission.builder()
                                 .userSeq(accessor.getUserSeq())
                                 .resourceSeq(resourceSeq)
                                 .shareSeq(shareSeq)
                                 .shareResourceSeq(shareResourceSeq)
                                 .shareType(shareType)
                                 .shareTargetSeq(shareTargetSeq)
                                 .shareTargetShareResourceSeq(shareTargetShareResourceSeq)
                                 .permissions(permissions)
                                 .isOwner(isOwner)
                                 .isShared(isShared)
                                 .resourceLocation(resourceLocation)
                                 .depth(depth)
                                 .build();
    }
    
    protected static Permissions buildPermissions(ResourcePermissionAccessor data, boolean useTarget, boolean hasLink) {
        // useTarget: 가장 가까운 link 공유에 내 target 이 달린 경우(또는 link 없는 SPECIFIC 의 target).
        if(useTarget){
            return Permissions.of(
                data.getShareTargetCanView(),
                data.getShareTargetCanEdit(),
                data.getShareTargetCanDownload(),
                data.getShareTargetCanUpload(),
                data.getShareTargetCanDelete(),
                data.getShareTargetCanShare()
            );
        }
        // 내 target 이 없으면 가장 가까운 link 공유의 권한을 사용한다.
        if(hasLink){
            return Permissions.of(
                data.getShareCanView(),
                data.getShareCanEdit(),
                data.getShareCanDownload(),
                data.getShareCanUpload(),
                data.getShareCanDelete(),
                data.getShareCanShare()
            );
        }
        return Permissions.invalid();
    }
    
    @Override
    public Integer getSharedByUserSeq() {
        return userSeq;
    }
}
