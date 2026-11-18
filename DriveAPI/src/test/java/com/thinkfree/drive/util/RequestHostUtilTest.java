package com.thinkfree.drive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestHostUtil — Host 헤더에서 포트·공백·대소문자 정규화")
class RequestHostUtilTest {

    @ParameterizedTest
    @CsvSource({
            "acme.drive.example.com,            acme.drive.example.com",
            "acme.drive.example.com:8080,       acme.drive.example.com",
            "acme.drive.example.com:443,        acme.drive.example.com",
            "ACME.Drive.Example.COM:8443,       acme.drive.example.com",
            "' acme.drive.example.com:8080 ',   acme.drive.example.com",
            "'acme.drive.example.com:8080, b.example.com:9090', acme.drive.example.com"})
    @DisplayName("Host 의 포트는 제거되고 소문자 host 만 남는다 (다중 값은 첫 값)")
    void stripsPortFromHost(String hostHeader, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.HOST, hostHeader);

        assertThat(RequestHostUtil.hostName(request)).isEqualTo(expected);
    }

    @Test
    @DisplayName("X-Forwarded-Host 가 있으면 Host 보다 우선하며 포트 역시 제거된다")
    void prefersForwardedHostAndStripsPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.HOST, "localhost:8080");
        request.addHeader("X-Forwarded-Host", "acme.drive.example.com:8443");

        assertThat(RequestHostUtil.hostName(request)).isEqualTo("acme.drive.example.com");
    }

    @Test
    @DisplayName("Host 가 없으면 null")
    void nullWhenNoHost() {
        assertThat(RequestHostUtil.hostName(new MockHttpServletRequest())).isNull();
    }
}
