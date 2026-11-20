package com.thinkfree.drive.restclient.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class RestRequestInterceptorTest {

    @Test
    void masksCredentialHeadersOnly() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("client-id", "client-secret");
        headers.add("Cookie", "session=abc");
        headers.add("X-Api-Key", "k");
        headers.add("X-Ticket-Service-Token", "t");
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "application/json");

        HttpHeaders masked = RestRequestInterceptor.masked(headers);

        assertThat(masked.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("****");
        assertThat(masked.getFirst("Cookie")).isEqualTo("****");
        assertThat(masked.getFirst("X-Api-Key")).isEqualTo("****");
        assertThat(masked.getFirst("X-Ticket-Service-Token")).isEqualTo("****");
        assertThat(masked.getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(masked.getFirst("Accept")).isEqualTo("application/json");
        assertThat(masked.toString()).doesNotContain("client-secret").doesNotContain(headers.getFirst(HttpHeaders.AUTHORIZATION).substring(6));
        assertThat(headers.getFirst("Cookie")).isEqualTo("session=abc");   // 원본은 그대로(실제 요청에 쓰임)
    }
}
