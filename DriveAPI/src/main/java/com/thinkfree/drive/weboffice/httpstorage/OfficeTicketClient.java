package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** TFO issues and consumes tickets. Drive never signs tickets or exposes the service credential. */
@Component
public class OfficeTicketClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpStorageProperties properties;
    private final RestClient client;

    public OfficeTicketClient(HttpStorageProperties properties) {
        this(properties, RestClient.builder());
    }

    /** Boot 의 Builder 를 써서 티켓 발급 호출이 트레이스 구간·메트릭으로 남고 TFO 에 traceparent 가 전달된다 */
    @Autowired
    public OfficeTicketClient(HttpStorageProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = restClientBuilder.clone().requestFactory(factory)
                // 기본 처리기는 오류 응답 본문을 예외 메시지에 넣고, 관측이 그 예외를 트레이스에 기록한다.
                //   본문에 서비스 토큰·일회용 URL 이 있을 수 있으므로 본문을 읽지 않는 예외로 바꾼다
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new HttpStorageException(HttpStatus.BAD_GATEWAY);
                })
                .build();
    }

    public record Issued(String url, long expiresAt) {}

    public Issued issue(String publicBase, String adapter, String documentPath, Map<String, String> params,
                        Map<String, Object> principal, String clientSessionId, String serviceToken) {
        try {
            // The authenticated, server-configured URL is never supplied by the FE.
            String base = validBase(publicBase);
            String credential = serviceToken;
            long ttl = properties.getOpenTtl().toSeconds();
            if (credential == null || credential.isBlank() || ttl < 1 || ttl > 43200)
                throw new IllegalStateException();
            String body = client.post().uri(base + "/api/open-tickets")
                    .headers(h -> h.setBearerAuth(credential))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("adapter", adapter, "documentPath", documentPath, "params", params,
                            "principal", principal, "context", Map.of("clientSessionId", clientSessionId,
                                    "clientType", "BROWSER", "documentSourceType", "STORAGE"),
                            "openTtlSeconds", ttl))
                    .retrieve().body(String.class);
            // JSON 파싱 오류 메시지에는 본문 일부가 들어간다 — 관측 밖에서 파싱해 트레이스에 남지 않게 한다
            JsonNode response = body == null ? null : JSON.readTree(body);
            if (response == null || !response.path("ticket").isTextual()
                    || response.path("ticket").asText().isBlank() || response.path("ticket").asText().length() > 16384
                    || !response.path("openExpiresAt").canConvertToLong()
                    || response.path("openExpiresAt").asLong() <= Instant.now().getEpochSecond())
                throw new IllegalStateException();
            String url = UriComponentsBuilder.fromUriString(base).path("/api/open-document")
                    .queryParam("ticket", response.path("ticket").asText()).build().encode().toUriString();
            return new Issued(url, response.path("openExpiresAt").asLong());
        } catch (Exception failure) {
            // Upstream errors may contain the service token or the one-use URL. Never relay or log them.
            throw new HttpStorageException(HttpStatus.BAD_GATEWAY);
        }
    }

    private static String validBase(String value) {
        URI uri = URI.create(value == null ? "" : value);
        if (!java.util.List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException();
        return value.replaceAll("/+$", "");
    }
}
