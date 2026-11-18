package com.thinkfree.drive.api.v1.admin.auditlog.enums;

import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.auditlog.enums.EventName;
import lombok.Getter;

import java.util.List;

/**
 * 감사 로그 조회 필터의 이벤트 종류 = 거버넌스에 기록된 eventType 값.
 * Drive가 기록하는 {@link EventName}과 이름이 같고, 로그인 성공/실패(LOGIN_SUCCESS·LOGIN_FAILED)만 전송 시 LOGIN 하나로 합쳐진다
 * (AuditLogPayloadToEventInMapper.resolveEventType — 성공/실패는 outcomeStatus로 구분).
 * <p>
 * 감사 기록 쪽 enum은 고치지 않고 여기서 연결만 한다. EventName이 추가되면 이 enum에도 넣어야 하며,
 * 누락은 AuditEventTypeTest가 잡는다.
 */
@Getter
public enum AuditEventType {
    LOGIN(EventName.LOGIN_SUCCESS, EventName.LOGIN_FAILED),
    LOGOUT(EventName.LOGOUT),
    ACCOUNT_LOCKED_STATUS_CHANGE(EventName.ACCOUNT_LOCKED_STATUS_CHANGE),

    FILE_CREATE(EventName.FILE_CREATE),
    FILE_UPLOAD(EventName.FILE_UPLOAD),
    FILE_DOWNLOAD(EventName.FILE_DOWNLOAD),
    FILE_PREVIEW(EventName.FILE_PREVIEW),
    FILE_OPEN(EventName.FILE_OPEN),
    FILE_EDIT(EventName.FILE_EDIT),
    FILE_DELETE(EventName.FILE_DELETE),
    FILE_PERMANENT_DELETE(EventName.FILE_PERMANENT_DELETE),
    FILE_RESTORE(EventName.FILE_RESTORE),
    FILE_RENAME(EventName.FILE_RENAME),
    FILE_MOVE(EventName.FILE_MOVE),
    FILE_COPY(EventName.FILE_COPY),

    FOLDER_CREATE(EventName.FOLDER_CREATE),
    FOLDER_DOWNLOAD(EventName.FOLDER_DOWNLOAD),
    FOLDER_DELETE(EventName.FOLDER_DELETE),
    FOLDER_PERMANENT_DELETE(EventName.FOLDER_PERMANENT_DELETE),
    FOLDER_RESTORE(EventName.FOLDER_RESTORE),
    FOLDER_RENAME(EventName.FOLDER_RENAME),

    SHARE_CREATE(EventName.SHARE_CREATE),
    SHARE_PERMISSION_CHANGE(EventName.SHARE_PERMISSION_CHANGE),
    SHARE_REMOVE(EventName.SHARE_REMOVE),

    USER_CREATE(EventName.USER_CREATE),
    USER_DELETE(EventName.USER_DELETE),
    USER_PERMANENT_DELETE(EventName.USER_PERMANENT_DELETE),
    USER_RESTORE(EventName.USER_RESTORE),
    USER_INFO_UPDATE(EventName.USER_INFO_UPDATE),
    USER_TWOFACTOR_RESET(EventName.USER_TWOFACTOR_RESET),
    USER_PASSWORD_CHANGE(EventName.USER_PASSWORD_CHANGE),
    USER_STATUS_CHANGE(EventName.USER_STATUS_CHANGE),

    ADMIN_CREATE(EventName.ADMIN_CREATE),
    ADMIN_DELETE(EventName.ADMIN_DELETE),
    ADMIN_PERMANENT_DELETE(EventName.ADMIN_PERMANENT_DELETE),
    ADMIN_RESTORE(EventName.ADMIN_RESTORE),
    ADMIN_INFO_UPDATE(EventName.ADMIN_INFO_UPDATE),
    ADMIN_PASSWORD_CHANGE(EventName.ADMIN_PASSWORD_CHANGE),
    ADMIN_STATUS_CHANGE(EventName.ADMIN_STATUS_CHANGE),

    SETTING_UPDATE(EventName.SETTING_UPDATE),
    SETTING_ALLOWED_IP_CHANGE(EventName.SETTING_ALLOWED_IP_CHANGE);

    /** 이 eventType으로 기록되는 Drive 이벤트들 */
    private final List<EventName> sources;
    /** 상위 분류 — 연결된 EventName의 분류를 그대로 따른다 */
    private final EventCategory eventCategory;

    AuditEventType(EventName... sources) {
        this.sources = List.of(sources);
        this.eventCategory = sources[0].getEventCategory();
    }
}
