package com.thinkfree.drive.basedomain.address;

import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BaseDomainAddress.parse — [scheme://]host[:port] 모양 검사·분해 (호스트 종류는 주소 모드가 판단)")
class BaseDomainAddressTest {

    @ParameterizedTest(name = "{0} → scheme={1}, host={2}, port={3}")
    @CsvSource(nullValues = "null", value = {
            "drive.example.com,                 null,  drive.example.com, null",
            "drive.example.com:8443,            null,  drive.example.com, 8443",
            "https://drive.example.com,         https, drive.example.com, null",
            "https://drive.example.com:8443,    https, drive.example.com, 8443",
            "http://192.168.0.10:8080,          http,  192.168.0.10,      8080",
            "192.168.0.10,                      null,  192.168.0.10,      null",
            "localhost:3000,                    null,  localhost,         3000",
            "HTTPS://Drive.Example.COM,         https, drive.example.com, null",
            "'  https://drive.example.com/  ',  https, drive.example.com, null",
            "https://drive.example.com:443,     https, drive.example.com, null",
            "http://drive.example.com:80,       http,  drive.example.com, null",
            "http://drive.example.com:443,      http,  drive.example.com, 443",
            "drive.example.com:443,             https, drive.example.com, null",   // 스킴 없는 443 → https 추론
            "drive.example.com:80,              http,  drive.example.com, null",   // 스킴 없는 80 → http 추론
            "192.168.0.10:80,                   http,  192.168.0.10,      null",
            "drive.example.com:8080,            null,  drive.example.com, 8080",   // 그 외 포트는 추론하지 않음
            "drive.example.com:1,               null,  drive.example.com, 1",
            "drive.example.com:65535,           null,  drive.example.com, 65535",
    })
    void parses(String input, String scheme, String host, Integer port) {
        BaseDomainAddress address = BaseDomainAddress.parse(input);

        assertThat(address.scheme()).isEqualTo(scheme);
        assertThat(address.host()).isEqualTo(host);
        assertThat(address.port()).isEqualTo(port);
    }

    @ParameterizedTest(name = "거부: {0}")
    @ValueSource(strings = {
            "ftp://drive.example.com",          // 스킴은 http·https 만
            "://drive.example.com",
            "https://",
            "drive.example.com:",                // 빈 포트
            "drive.example.com:0",               // 포트 범위
            "drive.example.com:65536",
            "drive.example.com:123456",
            "drive.example.com:80a",
            "https://drive.example.com/app",     // 경로
            "drive.example.com?x=1",             // 쿼리
            "drive.example.com#top",             // 프래그먼트
            "user@drive.example.com",            // userinfo
            "[::1]:8080",                        // IPv6 는 범위 밖
            "drive example.com",
            "drive.example.com:80:90",
    })
    void rejectsMalformed(String input) {
        assertThatThrownBy(() -> BaseDomainAddress.parse(input))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(BaseDomainErrorCode.INVALID_BASE_DOMAIN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlank(String input) {
        assertThatThrownBy(() -> BaseDomainAddress.parse(input)).isInstanceOf(ApiRuntimeException.class);
    }

    @Test
    @DisplayName("display 는 저장된 그대로 [scheme://]host[:port]")
    void display() {
        assertThat(BaseDomainAddress.parse("https://drive.example.com:8443").display()).isEqualTo("https://drive.example.com:8443");
        assertThat(BaseDomainAddress.parse("192.168.0.10:8080").display()).isEqualTo("192.168.0.10:8080");
        assertThat(BaseDomainAddress.parse("https://drive.example.com:443").display()).isEqualTo("https://drive.example.com");
        assertThat(BaseDomainAddress.parse("drive.example.com:80").display()).isEqualTo("http://drive.example.com");
        assertThat(BaseDomainAddress.display(null, null, null)).isNull();
    }
}
