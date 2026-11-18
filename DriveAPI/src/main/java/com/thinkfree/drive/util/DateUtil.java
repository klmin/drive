package com.thinkfree.drive.util;

import com.thinkfree.drive.global.time.DriveTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;

/**
 * 표시·파싱용 날짜 유틸. 시간대가 필요한 계산은 설정 시간대({@link DriveTime})를 쓴다 — 하루 경계·벽시계 변환은 {@link DriveTime} 이 정본.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class DateUtil {

    public static LocalDateTime parse(String date, String pattern) {
        try {
            return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(pattern));
        }catch(DateTimeParseException  | IllegalArgumentException e){
            log.error("[DateUtil parse Exception]", e);
            return null;
        }

    }

    public static String format(String date, String pattern, String outputFormat) {
        LocalDateTime dateTime = parse(date, pattern);
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ofPattern(outputFormat));
    }

    /** 설정 시간대 벽시계로 포맷(파일명 등 표시용). */
    public static String format(Instant date, String pattern) {
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(DriveTime.zone())
                .format(date);
    }

    /** date 가 속한 날(설정 시간대)에 plusDay 를 더한 날의 00:00. */
    public static Instant calcStartDay(Instant date, TemporalAmount plusDay) {
        return date.atZone(DriveTime.zone())
                   .plus(plusDay)
                   .toLocalDate()
                   .atStartOfDay(DriveTime.zone())
                   .toInstant();
    }

}
