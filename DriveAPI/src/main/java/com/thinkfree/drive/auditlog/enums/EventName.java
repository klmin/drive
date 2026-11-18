package com.thinkfree.drive.auditlog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventName {
    LOGIN_SUCCESS(EventCategory.LOGIN, CollectionShape.NONE),
    LOGIN_FAILED(EventCategory.LOGIN, CollectionShape.NONE),
    LOGOUT(EventCategory.LOGIN, CollectionShape.NONE),
    ACCOUNT_LOCKED_STATUS_CHANGE(EventCategory.LOGIN, CollectionShape.USER_CHANGE),

    FILE_CREATE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_UPLOAD(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_DOWNLOAD(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_PREVIEW(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_OPEN(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_EDIT(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_DELETE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_PERMANENT_DELETE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE_DELETE),
    FILE_RESTORE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FILE_RENAME(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE_CHANGE),
    FILE_MOVE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE_CHANGE),
    FILE_COPY(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),

    FOLDER_CREATE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FOLDER_DOWNLOAD(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FOLDER_DELETE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FOLDER_PERMANENT_DELETE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE_DELETE),
    FOLDER_RESTORE(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE),
    FOLDER_RENAME(EventCategory.FILE_FOLDER, CollectionShape.RESOURCE_CHANGE),

    SHARE_CREATE(EventCategory.SHARE, CollectionShape.SHARE_CREATE),
    SHARE_PERMISSION_CHANGE(EventCategory.SHARE, CollectionShape.SHARE_PERMISSION_CHANGE),
    SHARE_REMOVE(EventCategory.SHARE, CollectionShape.SHARE_REMOVE),

    USER_CREATE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_DELETE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_PERMANENT_DELETE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_RESTORE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_INFO_UPDATE(EventCategory.USER_MANAGEMENT, CollectionShape.USER_CHANGE),
    USER_TWOFACTOR_RESET(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_PASSWORD_CHANGE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    USER_STATUS_CHANGE(EventCategory.USER_MANAGEMENT, CollectionShape.USER_CHANGE),

    ADMIN_CREATE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    ADMIN_DELETE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    ADMIN_PERMANENT_DELETE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    ADMIN_RESTORE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    ADMIN_INFO_UPDATE(EventCategory.USER_MANAGEMENT, CollectionShape.USER_CHANGE),
    ADMIN_PASSWORD_CHANGE(EventCategory.USER_MANAGEMENT, CollectionShape.USER),
    ADMIN_STATUS_CHANGE(EventCategory.USER_MANAGEMENT, CollectionShape.USER_CHANGE),

    SETTING_UPDATE(EventCategory.SETTING, CollectionShape.SETTING_CHANGE),
    SETTING_ALLOWED_IP_CHANGE(EventCategory.SETTING, CollectionShape.VALUE_CHANGE);

    private final EventCategory eventCategory;
    private final CollectionShape collectionShape;
}
