package com.thinkfree.drive.global.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DriveTimeTest {

    @AfterEach
    void restore() {
        DriveTime.configure(ZoneId.of(DriveTime.DEFAULT_ZONE_ID));
    }

    @Test
    void 기본_시간대는_서울이고_하루_범위는_반열린_구간이다() {
        LocalDate day = LocalDate.of(2026, 10, 7);

        assertThat(DriveTime.zone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(DriveTime.startOfDay(day)).isEqualTo(Instant.parse("2026-10-06T15:00:00Z"));
        assertThat(DriveTime.endExclusive(day)).isEqualTo(Instant.parse("2026-10-07T15:00:00Z"));
    }

    @Test
    void 설정_시간대를_따른다() {
        DriveTime.configure(ZoneId.of("UTC"));
        LocalDate day = LocalDate.of(2026, 10, 7);

        assertThat(DriveTime.startOfDay(day)).isEqualTo(Instant.parse("2026-10-07T00:00:00Z"));
        assertThat(DriveTime.endExclusive(day)).isEqualTo(Instant.parse("2026-10-08T00:00:00Z"));
        assertThat(DriveTime.toInstant(LocalDateTime.of(2026, 10, 7, 9, 0))).isEqualTo(Instant.parse("2026-10-07T09:00:00Z"));
    }

    @Test
    void 벽시계와_Instant_왕복() {
        Instant instant = Instant.parse("2026-10-07T14:59:59.500Z");

        assertThat(DriveTime.toLocal(instant)).isEqualTo(LocalDateTime.of(2026, 10, 7, 23, 59, 59, 500_000_000));
        assertThat(DriveTime.toLocalDate(instant)).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(DriveTime.toInstant(DriveTime.toLocal(instant))).isEqualTo(instant);
    }

    @Test
    void null_은_null() {
        assertThat(DriveTime.toInstant(null)).isNull();
        assertThat(DriveTime.toLocal(null)).isNull();
        assertThat(DriveTime.startOfDay(null)).isNull();
        assertThat(DriveTime.endExclusive(null)).isNull();
        assertThat(DriveTime.toLocalDate(null)).isNull();
    }
}
