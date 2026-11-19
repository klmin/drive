package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
        client = restClientBuilder.clone().requestFactory(factory).build();
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
            JsonNode response = client.post().uri(base + "/api/open-tickets")
                    .headers(h -> h.setBearerAuth(credential))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("adapter", adapter, "documentPath", documentPath, "params", params,
                            "principal", principal, "context", Map.of("clientSessionId", clientSessionId,
                                    "clientType", "BROWSER", "documentSourceType", "STORAGE"),
                            "openTtlSeconds", ttl))
                    .retrieve().body(JsonNode.class);
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
