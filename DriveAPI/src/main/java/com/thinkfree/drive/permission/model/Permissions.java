package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.permission.enums.Permission;
import com.thinkfree.drive.permission.support.PermissionHolder;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;


@Builder
@Getter
public class Permissions implements PermissionHolder {
    
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    
    public static Permissions forOwner() {
        return Permissions.builder()
                          .canView(true)
                          .canEdit(true)
                          .canDownload(true)
                          .canUpload(true)
                          .canDelete(true)
                          .canShare(true)
                          .build();
    }
    
    public static Permissions invalid() {
        return Permissions.builder()
                          .canView(false)
                          .canEdit(false)
                          .canDownload(false)
                          .canUpload(false)
                          .canDelete(false)
                          .canShare(false)
                          .build();
    }
    
    public static Permissions of(Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canDelete, Boolean canShare) {
        return Permissions.builder()
                          .canView(canView)
                          .canEdit(canEdit)
                          .canDownload(canDownload)
                          .canUpload(canUpload)
                          .canDelete(canDelete)
                          .canShare(canShare)
                          .build();
    }
    
    public Set<Permission> toPermissionSet() {
        return Arrays.stream(Permission.values())
                     .filter(this::isGranted)
                     .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }
    
    private boolean isGranted(Permission permission) {
        return switch (permission) {
            case VIEW     -> Boolean.TRUE.equals(canView);
            case EDIT     -> Boolean.TRUE.equals(canEdit);
            case DOWNLOAD -> Boolean.TRUE.equals(canDownload);
            case UPLOAD   -> Boolean.TRUE.equals(canUpload);
            case DELETE   -> Boolean.TRUE.equals(canDelete);
            case SHARE    -> Boolean.TRUE.equals(canShare);
        };
    }
   
}
