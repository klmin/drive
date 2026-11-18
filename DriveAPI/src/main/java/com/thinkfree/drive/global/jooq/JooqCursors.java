package com.thinkfree.drive.global.jooq;

import com.thinkfree.drive.global.time.DriveTime;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/** 커서 목록 조회 보조 — 클라이언트가 되돌려 보낸 커서 값 해석(구 {@code global/jdbc/JdbcTimes.cursorTimestamp}, 10-30 작업 B P4 에서 옮김). */
public final class JooqCursors {

    private JooqCursors() {
    }

    /**
     * 날짜 정렬 커서 값 바인딩용 — SQL 은 {@code CAST(:cursorValue AS TIMESTAMPTZ)} 그대로 두고 값에 오프셋을 붙인다.
     * 커서 값은 직전 응답의 nextCursorValue(설정 시간대 벽시계, {@code yyyy-MM-ddTHH:mm:ss[.f]} 또는 MCP 의 {@code yyyy-MM-dd HH:mm:ss.SSS})라
     * 오프셋 없이 넘기면 PG 가 세션 TimeZone(= JVM 기본 시간대)으로 해석한다. 벽시계로 해석되지 않는 값(오프셋 포함 등)은 원문 그대로 넘긴다.
     * 이름·크기 정렬 커서에는 쓰지 않는다(날짜처럼 생긴 이름이 바뀌면 안 됨).
     */
    public static String timestamp(Object cursorValue) {
        String raw = String.valueOf(cursorValue);
        try {
            LocalDateTime local = LocalDateTime.parse(raw, LOCAL_CURSOR);
            return OffsetDateTime.ofInstant(DriveTime.toInstant(local), ZoneOffset.UTC).toString();
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    private static final DateTimeFormatter LOCAL_CURSOR = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendPattern("[ ]['T']")
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();
}
