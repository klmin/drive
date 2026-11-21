package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.exception.S3ConnectionPoolTimeout;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.observation.S3HttpPoolMetrics;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.S3HttpClientProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.storage.service.S3StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 동기 클라이언트의 HTTP 커넥션 풀 설정을 실제 Apache 클라이언트와 로컬 HTTP 서버로 확인한다.
 * 풀 1개를 GetObject 스트림으로 붙잡아 두고 다음 호출이 대기 초과로 실패하는지, 재시도 없이 1번에 끝나는지,
 * 서비스가 503(STORAGE_009)으로 바꾸는지, 지표가 남는지 본다.
 * <p>
 * 서버는 블로킹 소켓으로 직접 만든다 — JDK HttpServer(NIO 셀렉터)는 일부 Windows 환경에서 루프백 파이프를 못 만든다.
 */
@DisplayName("S3 HTTP 커넥션 풀 — 대기 초과는 재시도 없이 503")
class S3ClientConfigHttpPoolTest {

    private static final String BUCKET = "pool-test";
    private static final byte[] BODY = "hello-pool-test".getBytes(StandardCharsets.UTF_8);

    private ServerSocket server;
    private final AtomicInteger getHits = new AtomicInteger();
    private final AtomicInteger errorHits = new AtomicInteger();
    private SimpleMeterRegistry registry;
    private S3Client s3Client;
    private S3ConnectionProperty connection;

    @BeforeEach
    void setUp() throws Exception {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "pool-test-server");
        acceptor.setDaemon(true);
        acceptor.start();

        String endpoint = "http://127.0.0.1:" + server.getLocalPort();
        connection = new S3ConnectionProperty(endpoint, null, "access", "secret", BUCKET);
        S3HttpClientProperty http = new S3HttpClientProperty(1, Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofSeconds(5), true);
        S3ClientConfig config = new S3ClientConfig(new StorageProperty(StorageType.RUSTFS), connection, http);
        registry = new SimpleMeterRegistry();
        S3HttpPoolMetrics metrics = config.s3HttpPoolMetrics(registry);
        s3Client = config.s3Client(config.s3CredentialsProvider(), provider(null), provider(metrics));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (s3Client != null) {
            s3Client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("풀이 꽉 차면 대기 시간 뒤 재시도 없이 실패하고 대기 초과 지표가 오른다")
    void poolTimeoutIsNotRetried() throws Exception {
        try (ResponseInputStream<GetObjectResponse> held = s3Client.getObject(r -> r.bucket(BUCKET).key("ok"))) {
            assertThat(getHits).hasValue(1);

            long start = System.nanoTime();
            assertThatThrownBy(() -> s3Client.getObject(r -> r.bucket(BUCKET).key("ok")))
                    .isInstanceOf(SdkClientException.class)
                    .satisfies(e -> assertThat(S3ConnectionPoolTimeout.isCause(e)).isTrue());
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // 재시도했다면 대기(200ms) × 3 + 백오프 — 한 번만 기다렸는지 본다
            assertThat(elapsedMs).isLessThan(550);
            assertThat(getHits).hasValue(1);
            assertThat(registry.counter(S3HttpPoolMetrics.ACQUIRE_TIMEOUT).count()).isEqualTo(1.0);
        }

        // 풀을 돌려받으면 다시 성공하고, 빌린 시간·빌린 연결 수가 남는다
        try (var again = s3Client.getObject(r -> r.bucket(BUCKET).key("ok"))) {
            assertThat(again.readAllBytes()).isEqualTo(BODY);
        }
        assertThat(registry.timer(S3HttpPoolMetrics.ACQUIRE).count()).isGreaterThanOrEqualTo(2);
        assertThat(registry.summary(S3HttpPoolMetrics.LEASED).count()).isGreaterThanOrEqualTo(2);
        assertThat(registry.get(S3HttpPoolMetrics.MAX_CONNECTIONS).gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("풀 대기 초과는 서비스에서 503(STORAGE_009)으로 바뀐다 — 다운로드는 응답 전에 연결을 빌리므로 503 이 나간다")
    void poolTimeoutMapsTo503() throws Exception {
        S3StorageService storage = new S3StorageService(s3Client, Mockito.mock(S3AsyncClient.class), connection,
                new StorageProperty(StorageType.RUSTFS));
        try (var held = s3Client.getObject(r -> r.bucket(BUCKET).key("ok"))) {
            assertThatThrownBy(() -> storage.getFileData("ok"))
                    .isInstanceOfSatisfying(StorageException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(StorageErrorCode.CONNECTION_POOL_EXHAUSTED);
                        assertThat(e.getCode().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    });
        }
    }

    @Test
    @DisplayName("다른 오류(5xx)는 기존 STANDARD 대로 3회까지 재시도한다")
    void otherFailuresStillRetried() {
        assertThatThrownBy(() -> s3Client.getObject(r -> r.bucket(BUCKET).key("fail")))
                .isInstanceOf(S3Exception.class);
        assertThat(errorHits).hasValue(3);
    }

    /** S3 흉내 서버 — /{bucket}/fail 은 500, 그 밖은 200 + 본문. keep-alive 로 연결을 재사용한다 */
    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread handler = new Thread(() -> serve(socket), "pool-test-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            OutputStream out = socket.getOutputStream();
            String requestLine;
            while ((requestLine = in.readLine()) != null) {
                String header;
                do {
                    header = in.readLine(); // 헤더는 쓰지 않는다(GET 이라 본문 없음)
                } while (header != null && !header.isEmpty());
                if (requestLine.contains("/" + BUCKET + "/fail")) {
                    errorHits.incrementAndGet();
                    out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    getHits.incrementAndGet();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + BODY.length + "\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    out.write(BODY);
                }
                out.flush();
            }
        } catch (IOException ignored) {
            // 클라이언트가 연결을 닫음
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        Mockito.doAnswer(inv -> {
            if (value != null) {
                ((Consumer<T>) inv.getArgument(0)).accept(value);
            }
            return null;
        }).when(provider).ifAvailable(Mockito.any());
        return provider;
    }
}
