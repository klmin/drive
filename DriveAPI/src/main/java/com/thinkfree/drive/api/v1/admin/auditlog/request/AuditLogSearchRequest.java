package com.thinkfree.drive.api.v1.admin.auditlog.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.api.v1.admin.auditlog.enums.AuditEventType;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 감사 로그(거버넌스 증적) 목록 조회 필터. 거버넌스 EventQuery로 매핑된다(usecase 참고).
 * 프론트 필터 UI(사용자검색/기간/카테고리/타입/Channel)에 대응.
 *
 * <p>페이징은 공통 {@link com.thinkfree.drive.api.page.request.PagingRequest}(pi/ps)를 별도로 받는다.
 */
public record AuditLogSearchRequest(
        // 사용자 검색: 프론트 자동완성으로 유저를 "선택"해 확정된 이메일을 넘긴다(actorEmail 정확일치). 행위자 기준.
        @Schema(title = "Actor email", description = "Filter audit events by the email address of the user who performed the action.")
        String actorEmail,
        // 기간(발생 시각 기준). from 이상, to 이하(usecase에서 occurredTo는 다음날 00:00 미만으로 변환)
        @Schema(title = "Event date from", description = "First date in the audit-event date filter, in YYYY-MM-DD format.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Schema(title = "Event date through", description = "Last date in the audit-event date filter, in YYYY-MM-DD format.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        // 분류·종류·경로는 enum으로 받아 입구(웹 바인딩·MCP 스키마)에서 허용값을 강제한다 — 거버넌스 전송 직전에 name()으로 바꾼다
        @Schema(title = "Event category", description = "Filter audit events by login, file/folder, sharing, user management or settings.")
        EventCategory eventCategory,       // TFD 분류(FILE_FOLDER/SHARE/…) 정확일치
        @ArraySchema(arraySchema = @Schema(title = "Event types", description = "Add the specific audit-event types to include."), schema = @Schema(title = "Event type", description = "One audit-event type to include."))
        List<AuditEventType> eventTypes,   // 이벤트 종류(반복 지정 또는 쉼표 구분)
        @Schema(title = "Request channel", description = "Filter events by WEB, API, MCP or SYSTEM.")
        AccessMethod channel,              // WEB/API/MCP/SYSTEM
        @Schema(title = "Event outcome", description = "Filter audit events by the recorded outcome status.")
        String outcomeStatus               // SUCCESS/FAIL
) {
}
