package com.thinkfree.drive.global.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 시간대가 결과를 바꾸는 변환의 단일 지점 — 설정값 {@code drive.time-zone}(기본 {@value #DEFAULT_ZONE_ID}).
 * <p>
 * 도메인·DB 는 {@link Instant}(절대 시각)라 시간대와 무관하다. 시간대가 필요한 곳은 셋뿐이다.
 * <ul>
 *   <li>클라이언트 요청의 LocalDate·LocalDateTime → Instant(요청 → Command 변환 지점)</li>
 *   <li>달력 하루 범위 {@code [startOfDay, endExclusive)} — 년월일 검색</li>
 *   <li>표시용 벽시계(파일명 등) — Instant → LocalDateTime</li>
 * </ul>
 * JVM 기본 시간대({@code ZoneId.systemDefault()})는 쓰지 않는다. 서버 OS 시간대에 따라 결과가 바뀌기 때문이다.
 * 값은 기동 시 {@link DriveTimeConfig} 가 주입한다. 스프링 밖(단위 테스트)에서는 yml 기본값과 같은 {@value #DEFAULT_ZONE_ID}.
 */
public final class DriveTime {

    /** yml {@code drive.time-zone} 기본값과 같아야 한다. */
    public static final String DEFAULT_ZONE_ID = "Asia/Seoul";

    private static volatile ZoneId zone = ZoneId.of(DEFAULT_ZONE_ID);

    private DriveTime() {
    }

    static void configure(ZoneId configured) {
        zone = Objects.requireNonNull(configured, "drive.time-zone");
    }

    public static ZoneId zone() {
        return zone;
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(zone).toInstant();
    }

    public static LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, zone);
    }

    /** 달력 하루의 시작(포함). */
    public static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }

    /** 달력 하루의 끝(제외) = 다음날 00:00. 23:59:59 로 막으면 timestamptz 의 소수 초가 빠진다. */
    public static Instant endExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** 달력 하루의 마지막 초(23:59:59) — 만료일처럼 "그 날까지" 를 저장할 때. 범위 검색은 {@link #endExclusive}. */
    public static Instant endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant();
    }

    /** 그 시각이 속한 달력 날짜. */
    public static LocalDate toLocalDate(Instant value) {
        return value == null ? null : LocalDate.ofInstant(value, zone);
    }
}
