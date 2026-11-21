package com.thinkfree.drive.storage.exception;

import org.apache.http.conn.ConnectionPoolTimeoutException;

/**
 * S3 동기 클라이언트(Apache HTTP)가 커넥션 풀에서 연결을 못 빌려 실패했는지 판별한다.
 * SDK 는 이를 {@code SdkClientException("Unable to execute HTTP request: Timeout waiting for connection from pool")}
 * 으로 감싸 던지고, 원인 사슬에 {@link ConnectionPoolTimeoutException} 이 남는다.
 */
public final class S3ConnectionPoolTimeout {

    /** 원인 사슬이 비정상적으로 길거나 순환할 때를 대비한 상한 */
    private static final int MAX_DEPTH = 10;

    private S3ConnectionPoolTimeout() {
    }

    public static boolean isCause(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof ConnectionPoolTimeoutException) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }
}
