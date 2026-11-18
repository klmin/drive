package com.thinkfree.drive.permission.support;

public interface PermissionHolder {
    Boolean getCanView();
    Boolean getCanEdit();
    Boolean getCanDownload();
    Boolean getCanUpload();

    Boolean getCanDelete();
    Boolean getCanShare();
}
