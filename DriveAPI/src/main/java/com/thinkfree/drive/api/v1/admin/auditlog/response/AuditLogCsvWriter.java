package com.thinkfree.drive.api.v1.admin.auditlog.response;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 감사 로그 CSV 본문을 응답 스트림에 쓰는 콜백.
 * <p>
 * {@code StreamingResponseBody}를 쓰지 않는 이유는 그쪽이 서블릿 비동기로 처리돼
 * {@code spring.mvc.async.request-timeout}(5분)에 걸리기 때문이다 — 내보내기는 그보다 오래 걸릴 수 있어
 * 컨트롤러가 요청 스레드에서 직접 쓴다. 동시 실행은 유스케이스가 따로 제한한다.
 */
@FunctionalInterface
public interface AuditLogCsvWriter {

    void writeTo(OutputStream output) throws IOException;
}
