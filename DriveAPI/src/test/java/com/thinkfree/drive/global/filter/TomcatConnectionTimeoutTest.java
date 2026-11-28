package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * server.tomcat.connection-timeout — 바이트를 멈춘 업로드 연결이 설정 시간 뒤 끊기고 업로드 허가가 반납되는지(실제 내장 Tomcat).
 * application.yml 과 같은 경로(ServerProperties → TomcatWebServerFactoryCustomizer)로 값을 넣는다.
 */
@DisplayName("Tomcat connection-timeout — 멈춘 업로드 본문은 끊기고 허가 반납")
class TomcatConnectionTimeoutTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private WebServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void stalledUploadBodyIsCutAndPermitReturned() throws Exception {
        UploadConcurrencyLimitFilter filter = new UploadConcurrencyLimitFilter(1, new ObjectMapper(), new SimpleMeterRegistry());
        CountDownLatch reading = new CountDownLatch(1);
        AtomicReference<Throwable> readError = new AtomicReference<>();

        ServerProperties properties = new ServerProperties();
        properties.getTomcat().setConnectionTimeout(TIMEOUT);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        new TomcatWebServerFactoryCustomizer(new MockEnvironment(), properties).customize(factory);
        server = factory.getWebServer(context -> {
            context.addFilter("uploadLimit", filter).addMappingForUrlPatterns(null, false, "/*");
            context.addServlet("parts", new HttpServlet() {
                @Override
                protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                    reading.countDown();
                    try {
                        req.getInputStream().readAllBytes();
                        resp.setStatus(200);
                    } catch (IOException e) {
                        readError.set(e);
                        throw e;
                    }
                }
            }).addMapping("/*");
        });
        server.start();

        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("PUT /api/v1/uploads/s1/parts/1 HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/octet-stream\r\nContent-Length: 1000\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(new byte[10]); // 1000 바이트 중 10 바이트만 보내고 멈춘다
            out.flush();

            assertThat(reading.await(5, TimeUnit.SECONDS)).as("본문 읽기 시작(허가 잡음)").isTrue();
            assertThat(filter.activeCount()).isEqualTo(1);

            long start = System.nanoTime();
            assertThat(waitUntil(() -> filter.activeCount() == 0, Duration.ofSeconds(8))).as("허가 반납").isTrue();
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            assertThat(took).as("설정 시간 근처에서 끊김").isLessThan(TIMEOUT.plusSeconds(4));
            assertThat(readError.get()).as("본문 읽기가 타임아웃으로 실패").isNotNull();
            assertThat(rootCause(readError.get())).isInstanceOf(SocketTimeoutException.class);

            assertThat(drainUntilClosed(socket.getInputStream())).as("서버가 연결을 닫음").isTrue();
        }
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration max) throws InterruptedException {
        long deadline = System.nanoTime() + max.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    /** 응답(있으면)을 읽고 EOF 까지 — 닫히면 true, 읽기 타임아웃이면 false */
    private static boolean drainUntilClosed(InputStream in) throws IOException {
        byte[] buf = new byte[1024];
        try {
            while (in.read(buf) >= 0) {
                // 오류 응답 본문은 버린다
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true; // RST 도 닫힘
        }
    }
}
