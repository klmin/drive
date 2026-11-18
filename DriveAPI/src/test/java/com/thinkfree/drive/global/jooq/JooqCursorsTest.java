package com.thinkfree.drive.global.jooq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.time.DriveIsoLocalDateTimeFormat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JooqCursorsTest {

    @Test
    void 날짜_커서는_설정_시간대_벽시계로_해석해_오프셋을_붙인다() {
        // 웹 응답 형식(ISO 'T')·MCP 형식(공백 + 밀리초) 둘 다, 기본 시간대 Asia/Seoul
        assertThat(OffsetDateTime.parse(JooqCursors.timestamp("2026-10-07T09:00:00")).toInstant())
                .isEqualTo(Instant.parse("2026-10-07T00:00:00Z"));
        assertThat(OffsetDateTime.parse(JooqCursors.timestamp("2026-10-07 09:00:00.123")).toInstant())
                .isEqualTo(Instant.parse("2026-10-07T00:00:00.123Z"));
        assertThat(OffsetDateTime.parse(JooqCursors.timestamp(LocalDateTime.of(2026, 10, 7, 9, 0, 0, 456_789_000))).toInstant())
                .as("CursorPageInfo 가 내보낸 LocalDateTime 객체 그대로")
                .isEqualTo(Instant.parse("2026-10-07T00:00:00.456789Z"));
    }

    @Test
    void 벽시계가_아닌_값은_원문_그대로() {
        assertThat(JooqCursors.timestamp("2026-10-07T09:00:00+09:00")).isEqualTo("2026-10-07T09:00:00+09:00");
        assertThat(JooqCursors.timestamp("garbage")).isEqualTo("garbage");
    }

    record IsoProbe(@DriveIsoLocalDateTimeFormat Instant at) {
    }

    @Test
    void Instant_를_LocalDateTime_시절과_같은_ISO_로컬_문자열로_직렬화() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new IsoProbe(Instant.parse("2026-10-07T00:00:00Z")));

        assertThat(json).isEqualTo("{\"at\":\"2026-10-07T09:00:00\"}");
    }
}
