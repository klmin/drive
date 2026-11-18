package com.thinkfree.drive.permission.enums;

import com.thinkfree.drive.permission.support.PermissionHolder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SharePermissionType {
    
    VIEW(PermissionHolder::getCanView),
    EDIT(PermissionHolder::getCanEdit),
    DOWNLOAD(PermissionHolder::getCanDownload),
    UPLOAD(PermissionHolder::getCanUpload),
    DELETE(PermissionHolder::getCanDelete),
    SHARE(PermissionHolder::getCanShare)
    ;
    
    private final Function<PermissionHolder, Boolean> checker;

    public boolean has(PermissionHolder permission) {
        return checker.apply(permission);
    }
}
