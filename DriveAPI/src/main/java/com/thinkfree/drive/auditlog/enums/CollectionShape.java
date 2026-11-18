package com.thinkfree.drive.auditlog.enums;

/**
 * eventName별 수집 항목(공통 필드 외 추가로 채워야 하는 AuditLogContext 필드) 조합.
 * 호출부는 AuditLogContext.builder()로 아래 필드만 채우면 된다.
 * 각 shape 옆에 어떤 EventName이 이 shape를 쓰는지 명시한다(EventName.java의 매핑과 일치해야 함).
 */
public enum CollectionShape {

    // 추가 항목 없음 (공통 필드만)
    // LOGIN: LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT
    NONE,

    // objectSeq, objectName, resourcePath
    // FILE_FOLDER: FILE_CREATE, FILE_UPLOAD, FILE_DOWNLOAD, FILE_PREVIEW, FILE_OPEN, FILE_EDIT, FILE_DELETE,
    //              FILE_RESTORE, FILE_COPY, FOLDER_CREATE, FOLDER_DOWNLOAD, FOLDER_DELETE, FOLDER_RESTORE
    RESOURCE,

    // objectSeq, objectName (resourcePath 없음)
    // FILE_FOLDER: FILE_PERMANENT_DELETE, FOLDER_PERMANENT_DELETE
    RESOURCE_DELETE,

    // objectSeq, objectName, resourcePath, beforeValue, afterValue
    // FILE_FOLDER: FILE_RENAME, FILE_MOVE, FOLDER_RENAME
    RESOURCE_CHANGE,

    // SHARE 계열 공통: objectSeq, objectName, resourcePath, targetTenantSeq, targetTenantName, targetUserSeq, targetUserId, targetUserName
    // (target* 는 대상자별 row에만 있고 링크 레벨 row에는 없음, targetTenant* 는 외부 테넌트일 때만)

    // SHARE 공통 + afterValue (부여된 권한 스냅샷, beforeValue 없음)
    // SHARE: SHARE_CREATE
    SHARE_CREATE,

    // SHARE 공통 + beforeValue (해제 전 권한 스냅샷, afterValue 없음)
    // SHARE: SHARE_REMOVE
    SHARE_REMOVE,

    // SHARE 공통 + beforeValue, afterValue
    // SHARE: SHARE_PERMISSION_CHANGE
    SHARE_PERMISSION_CHANGE,

    // objectSeq, objectUserId, objectName
    // USER_MANAGEMENT: USER_CREATE, USER_DELETE, USER_PERMANENT_DELETE, USER_RESTORE, USER_TWOFACTOR_RESET, USER_PASSWORD_CHANGE,
    //                  ADMIN_CREATE, ADMIN_DELETE, ADMIN_PERMANENT_DELETE, ADMIN_RESTORE, ADMIN_PASSWORD_CHANGE
    USER,

    // USER + beforeValue, afterValue
    // LOGIN: ACCOUNT_LOCKED_STATUS_CHANGE
    // USER_MANAGEMENT: USER_INFO_UPDATE, USER_STATUS_CHANGE, ADMIN_INFO_UPDATE, ADMIN_STATUS_CHANGE
    USER_CHANGE,

    // beforeValue, afterValue
    // SETTING: SETTING_ALLOWED_IP_CHANGE
    VALUE_CHANGE,

    // objectSeq, objectName, beforeValue, afterValue (objectUserId 없음)
    // SETTING: SETTING_UPDATE
    SETTING_CHANGE
}
