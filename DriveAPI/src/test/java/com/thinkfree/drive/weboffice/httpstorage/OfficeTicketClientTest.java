package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OfficeTicketClientTest {
    HttpServer server;
    HttpStorageProperties properties;
    String officeBase;
    String serviceToken;
    final ObjectMapper json = new ObjectMapper();
    AtomicReference<String> auth = new AtomicReference<>();
    AtomicReference<Map> body = new AtomicReference<>();
    int status = 200;
    String response;
    @BeforeEach void setup() throws Exception {
        properties = new HttpStorageProperties();
        serviceToken = "test-service-credential";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        officeBase = "http://127.0.0.1:" + server.getAddress().getPort() + "/cloud-office";
        response = json.writeValueAsString(Map.of("ticket", "issued-by-tfo", "openExpiresAt", Instant.now().plusSeconds(60).getEpochSecond()));
        server.createContext("/cloud-office/api/open-tickets", x -> {
            auth.set(x.getRequestHeaders().getFirst("Authorization"));
            body.set(json.readValue(x.getRequestBody(), Map.class));
            x.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            x.sendResponseHeaders(status, bytes.length); x.getResponseBody().write(bytes); x.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    OfficeTicketClient.Issued issue() {
        return new OfficeTicketClient(properties).issue(officeBase, "drive-local",
                "documents/2-3/a.docx", Map.of("app", "WORD_EDITOR", "docId", "2-3"),
                Map.of("tenantId", "drive:1", "userId", "2"), "browser-session", serviceToken);
    }
    @Test void usesServerCredentialAndReturnsOnlyTfoUrlAndDeadline() {
        var issued = issue();
        assertEquals("Bearer test-service-credential", auth.get());
        assertEquals(60, body.get().get("openTtlSeconds"));
        assertEquals("documents/2-3/a.docx", body.get().get("documentPath"));
        assertEquals(officeBase + "/api/open-document?ticket=issued-by-tfo", issued.url());
        assertFalse(issued.url().contains("credential"));
    }
    @Test void providerFailureDoesNotLeakBodyOrCredential() {
        status = 401; response = "secret upstream error";
        var error = assertThrows(HttpStorageException.class, this::issue);
        assertEquals(502, error.status().value()); assertFalse(error.getMessage().contains("secret"));
    }
    @Test void expiredAndMissingTicketsAreRejected() {
        response = "{\"ticket\":\"t\",\"openExpiresAt\":1}";
        assertThrows(HttpStorageException.class, this::issue);
        response = "{}"; assertThrows(HttpStorageException.class, this::issue);
    }
    @Test void missingServiceConfigurationFailsWithoutRequest() {
        serviceToken = ""; assertThrows(HttpStorageException.class, this::issue); assertNull(body.get());
    }
    @Test void ticketCallIsObservedAsChildOfCurrentObservation() {
        var stopped = new java.util.concurrent.CopyOnWriteArrayList<io.micrometer.observation.Observation.Context>();
        var registry = io.micrometer.observation.ObservationRegistry.create();
        registry.observationConfig().observationHandler(new io.micrometer.observation.ObservationHandler<>() {
            @Override public void onStop(io.micrometer.observation.Observation.Context c) { stopped.add(c); }
            @Override public boolean supportsContext(io.micrometer.observation.Observation.Context c) { return true; }
        });
        var request = io.micrometer.observation.Observation.start("request", registry);
        try (var ignored = request.openScope()) {
            new OfficeTicketClient(properties, org.springframework.web.client.RestClient.builder().observationRegistry(registry)).issue(officeBase, "drive-local", "documents/2-3/a.docx",
                    Map.of("app", "WORD_EDITOR", "docId", "2-3"), Map.of("tenantId", "drive:1", "userId", "2"),
                    "browser-session", serviceToken);
        } finally { request.stop(); }
        var call = stopped.stream().filter(c -> "http.client.requests".equals(c.getName())).findFirst().orElseThrow();
        assertSame(request, call.getParentObservation());
        assertEquals("POST", call.getLowCardinalityKeyValue("method").getValue());
        assertEquals("200", call.getLowCardinalityKeyValue("status").getValue());
    }
    /** 관측이 기록한 오류(원인 포함)의 메시지 전부 — 트레이스에 예외 이벤트로 남는 내용 */
    String observedErrorText() {
        var stopped = new java.util.concurrent.CopyOnWriteArrayList<io.micrometer.observation.Observation.Context>();
        var registry = io.micrometer.observation.ObservationRegistry.create();
        registry.observationConfig().observationHandler(new io.micrometer.observation.ObservationHandler<>() {
            @Override public void onStop(io.micrometer.observation.Observation.Context c) { stopped.add(c); }
            @Override public boolean supportsContext(io.micrometer.observation.Observation.Context c) { return true; }
        });
        var error = assertThrows(HttpStorageException.class, () -> new OfficeTicketClient(properties,
                org.springframework.web.client.RestClient.builder().observationRegistry(registry)).issue(officeBase,
                "drive-local", "documents/2-3/a.docx", Map.of("app", "WORD_EDITOR", "docId", "2-3"),
                Map.of("tenantId", "drive:1", "userId", "2"), "browser-session", serviceToken));
        assertFalse(String.valueOf(error.getMessage()).contains("secret"));
        var call = stopped.stream().filter(c -> "http.client.requests".equals(c.getName())).findFirst().orElseThrow();
        var text = new StringBuilder();
        for (Throwable t = call.getError(); t != null; t = t.getCause()) text.append(t).append('\n');
        return text.toString();
    }
    @Test void providerErrorBodyIsNotRecordedInObservation() {
        status = 401; response = "secret upstream error";
        assertFalse(observedErrorText().contains("secret"));
    }
    @Test void malformedSuccessBodyIsNotRecordedInObservation() {
        response = "secret-not-json";
        assertFalse(observedErrorText().contains("secret"));
    }
    @Test void invalidBaseUrlDoesNotSendCredential() {
        officeBase = "http://user:password@localhost/";
        assertThrows(HttpStorageException.class, this::issue); assertNull(body.get());
    }
}
