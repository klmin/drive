package com.thinkfree.drive.storage.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3 동기 클라이언트(업로드·다운로드·삭제 등 모든 동기 S3 호출)의 HTTP 커넥션 풀 설정({@code storage.http.*}).
 * <p>
 * 동기 SDK 는 스레드 풀이 없고 커넥션 풀만 있다 — 부른 스레드가 직접 통신하고 연결만 풀에서 빌린다.
 * 다운로드 1건은 본문 전송이 끝날 때까지, 업로드 파트 1건은 파트 전송 동안 연결 1개를 점유한다.
 * <ul>
 *   <li>동시 수 제한은 입구(다운로드 스트림 풀 {@code drive.download.stream.max-threads} 등)에서 하고,
 *       이 풀은 그보다 크게 잡은 안전망이다. 다운로드 상한을 바꾸면 이 값도 같이 정한다</li>
 *   <li>풀은 WAS 인스턴스마다 따로라, 스토리지가 받는 연결 수는 {@code maxConnections × WAS 대수}</li>
 *   <li>연결을 못 빌리면 {@code connectionAcquisitionTimeout} 뒤 재시도 없이 503(STORAGE_009)</li>
 * </ul>
 *
 * @param maxConnections               풀 최대 연결 수(SDK 기본 50)
 * @param connectionAcquisitionTimeout 풀에서 연결을 빌릴 때 기다리는 최대 시간(SDK 기본 10초)
 * @param connectionTimeout            TCP 연결 수립 제한
 * @param socketTimeout                응답 읽기·쓰기 중 무응답 제한
 * @param tcpKeepAlive                 TCP keep-alive 사용
 */
@ConfigurationProperties("storage.http")
public record S3HttpClientProperty
(
    Integer maxConnections,
    Duration connectionAcquisitionTimeout,
    Duration connectionTimeout,
    Duration socketTimeout,
    Boolean tcpKeepAlive
)
{
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    public static final Duration DEFAULT_CONNECTION_ACQUISITION_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofSeconds(30);

    public S3HttpClientProperty {
        maxConnections = maxConnections == null ? DEFAULT_MAX_CONNECTIONS : maxConnections;
        connectionAcquisitionTimeout = connectionAcquisitionTimeout == null ? DEFAULT_CONNECTION_ACQUISITION_TIMEOUT : connectionAcquisitionTimeout;
        connectionTimeout = connectionTimeout == null ? DEFAULT_CONNECTION_TIMEOUT : connectionTimeout;
        socketTimeout = socketTimeout == null ? DEFAULT_SOCKET_TIMEOUT : socketTimeout;
        tcpKeepAlive = tcpKeepAlive == null ? Boolean.TRUE : tcpKeepAlive;
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("storage.http.max-connections must be positive: " + maxConnections);
        }
    }

    public static S3HttpClientProperty defaults() {
        return new S3HttpClientProperty(null, null, null, null, null);
    }
}
