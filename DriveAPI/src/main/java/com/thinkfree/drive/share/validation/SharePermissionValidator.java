package com.thinkfree.drive.share.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.permission.enums.Permission;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.model.Permissions;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class SharePermissionValidator {

    /**
     * 링크 공유(리소스 기준) 권한 검증.
     * 공유 유형(제한됨/조직내부/전체공개)별 매트릭스 안의 권한만 부여할 수 있다.
     */
    public void validate(ShareType shareType, ResourceType resourceType, Permissions permissions) {
        validateAgainst(shareType.allowedPermissions(resourceType), permissions);
    }

    /**
     * 개별 공유 대상(target) 권한 검증.
     * 공유 유형 제약을 받지 않고, 리소스 유형에 유효한 전체 권한을 부여할 수 있다(타겟 기준 = 모두 가능).
     * VIEW 필수 + 전제권한은 그대로 유지하며, DELETE 는 스펙상 부여 대상이 아니다(요청에서 항상 false).
     */
    public void validateTarget(ResourceType resourceType, Permissions permissions) {
        validateAgainst(grantableTargetPermissions(resourceType), permissions);
    }

    /** 리소스 유형에 유효한 부여 가능 권한 전체(UPLOAD 는 폴더만, DELETE 제외). */
    private static Set<Permission> grantableTargetPermissions(ResourceType resourceType) {
        return resourceType.isFile()
                ? EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.DOWNLOAD, Permission.SHARE)
                : EnumSet.of(Permission.VIEW, Permission.EDIT, Permission.UPLOAD, Permission.DOWNLOAD, Permission.SHARE);
    }

    private void validateAgainst(Set<Permission> allowed, Permissions permissions) {

        Set<Permission> requestedPermissions = permissions.toPermissionSet();

        if (!requestedPermissions.contains(Permission.VIEW)) {
            throw ApiRuntimeException.type(PermissionErrorCode.MISSING_VIEW);
        }

        for (Permission permission : requestedPermissions) {
            if (!allowed.contains(permission)) {
                throw ApiRuntimeException.type(PermissionErrorCode.NOT_ALLOWED_IN_CONTEXT);
            }

            for (Permission req : permission.getPrerequisites()) {
                if (!requestedPermissions.contains(req)) {
                    throw ApiRuntimeException.type(PermissionErrorCode.INVALID_PREREQUISITE);
                }
            }
        }

    }
}
