package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.common.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** getServerTimeMS 의 ms 변환이 구 SQL {@code (EXTRACT(EPOCH FROM ts) * 1000)::bigint}(반올림)과 같은 값인지 — 고정 시각으로 반올림·자리올림 경계를 본다. */
@DisplayName("License.getServerTimeMS — epoch ms 반올림이 구 SQL 과 같음(PostgreSQL Testcontainers)")
class LicenseServerTimeMsTest extends RepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LicenseQueryRepository repository;

    @Test
    @DisplayName("반올림·자리올림·정확한 ms 경계에서 구 SQL 과 같은 값")
    void sameAsLegacyExpression() {
        for (String ts : List.of("2026-10-30 13:18:16.000000+09", "2026-10-30 13:18:16.000499+09", "2026-10-30 13:18:16.000500+09",
                "2026-10-30 13:18:16.123456+09", "2026-10-30 13:18:16.999500+09", "2026-10-30 13:18:16.999999+00", "1970-01-01 00:00:00.000700+00")) {
            Long legacy = jdbc.queryForObject("SELECT (EXTRACT(EPOCH FROM CAST(? AS timestamptz)) * 1000)::bigint", Long.class, ts);
            OffsetDateTime db = jdbc.queryForObject("SELECT CAST(? AS timestamptz)", OffsetDateTime.class, ts);
            assertThat(DefaultLicenseQueryRepository.epochMillisRounded(db.toInstant())).as(ts).isEqualTo(legacy);
        }
    }

    @Test
    @DisplayName("실제 호출은 앞뒤 clock_timestamp 사이(ms)")
    void currentDbClock() {
        long before = jdbc.queryForObject("SELECT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint", Long.class);
        Long now = repository.getServerTimeMS();
        long after = jdbc.queryForObject("SELECT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint", Long.class);
        assertThat(now).isBetween(before, after);
    }
}
