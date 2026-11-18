package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.time.DriveTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MCP 툴 컨버터 공용 날짜 변환 — MCP 경로는 Spring AI 내부 ObjectMapper가 직렬화해
 * 웹의 @DriveDateTimeFormat이 적용되지 않으므로(LocalDateTime이면 배열로 나감),
 * 웹과 같은 포맷의 String으로 확정해 넣는다. Instant 는 설정 시간대 벽시계, LocalDateTime(커서 값)은 그대로.
 */
public interface ToolDateTimeSupport {

    DateTimeFormatter TOOL_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DriveConstants.YYYY_MM_DD_HH_MM_SS_SSS);
    DateTimeFormatter TOOL_DATE_FORMATTER = DateTimeFormatter.ofPattern(DriveConstants.YYYY_MM_DD);

    // MapStruct가 LocalDateTime→String 필드 매핑에 자동 사용
    default String toDateTimeString(LocalDateTime dateTime) {
        return dateTime == null ? null : TOOL_DATE_TIME_FORMATTER.format(dateTime);
    }

    // Instant 필드(신규 객체 날짜 방침 — 삭제 대기 시각 등)용 — 웹 @DriveDateTimeFormat과 동일하게 설정 시간대(drive.time-zone) 벽시계로 포맷
    default String toDateTimeString(Instant instant) {
        return instant == null ? null : toDateTimeString(DriveTime.toLocal(instant));
    }

    // 날짜만 있는 필드(공유 만료일 등)용 — 웹 @DriveDateFormat과 동일 포맷
    default String toDateString(LocalDate date) {
        return date == null ? null : TOOL_DATE_FORMATTER.format(date);
    }

    // 날짜 정렬 커서의 nextCursorValue도 배열 대신 String으로 — 도메인 커서 게이트는 웹과 동일하게 문자열 값을 수용한다
    default Object toCursorValue(Object nextCursorValue) {
        return nextCursorValue instanceof LocalDateTime dateTime ? toDateTimeString(dateTime) : nextCursorValue;
    }

}
