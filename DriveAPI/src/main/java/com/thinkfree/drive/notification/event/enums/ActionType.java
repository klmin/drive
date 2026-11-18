package com.thinkfree.drive.notification.event.enums;

import com.thinkfree.drive.notification.payload.NotificationPayload;
import com.thinkfree.drive.notification.payload.impl.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ActionType {
    SHARED("공유됨", SharedPayload.class),
    UNSHARED("공유 해제됨", UnsharedPayload.class),
    STORAGE_WARNING("저장 용량", StorageWarningPayload.class),
    STORAGE_EXCEEDED("저장 용량 초과", StorageExceededPayload.class),
    FILE_UPLOAD("공유 폴더 업로드", FileUploadPayload.class),
    ;
    
    private final String description;
    private final Class<? extends NotificationPayload> payloadClass;
}
