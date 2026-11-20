package com.thinkfree.drive.api.v1.admin.auditlog.response;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 감사 로그 CSV 본문을 응답 스트림에 쓰는 콜백.
 * <p>
 * {@code StreamingResponseBody}(서블릿 비동기 — 다운로드 스트림 풀·{@code spring.mvc.async.request-timeout} 적용)를 쓰지 않고
 * 컨트롤러가 요청 스레드에서 직접 쓴다. 동시 실행은 유스케이스가 따로 제한한다.
 */
@FunctionalInterface
public interface AuditLogCsvWriter {

    void writeTo(OutputStream output) throws IOException;
}
