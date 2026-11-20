package com.thinkfree.drive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogSafe — 로그 인자의 줄바꿈 제거(로그 위조 방지)")
class LogSafeTest {

    @Test
    @DisplayName("CR·LF 는 '_' 로 바꿔 한 줄로, 다른 문자는 그대로, null 은 \"null\"")
    void stripsLineBreaks() {
        assertThat(LogSafe.of("/api/v1/files\r\n[INFO] forged entry")).isEqualTo("/api/v1/files__[INFO] forged entry");
        assertThat(LogSafe.of("a\nb\rc")).isEqualTo("a_b_c");
        assertThat(LogSafe.of("tab\tand 한글 /path?q=1")).isEqualTo("tab\tand 한글 /path?q=1");
        assertThat(LogSafe.of(null)).isEqualTo("null");
        assertThat(LogSafe.of(42)).isEqualTo("42");
    }
}
