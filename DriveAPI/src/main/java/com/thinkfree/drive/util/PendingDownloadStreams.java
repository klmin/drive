package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import software.amazon.awssdk.http.Abortable;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 요청 스레드에서 미리 열어 둔 다운로드 스트림(S3 GetObject 응답 등)을 요청 속성에 기록해 둔다.
 *
 * <p>{@code StreamingResponseBody} 본문은 다운로드 스트림 풀(StreamingAsyncConfig)에서 실행되며 스트림을 직접 닫는다.
 * 그런데 풀이 포화돼 작업 제출이 거절되면 본문이 한 번도 실행되지 않아, 이미 열린 스토리지 응답이 닫히지 않고 연결이 남는다.
 * 그래서 거절(503) 처리기에서 {@link #closeAll(RequestAttributes)} 로 이 요청이 연 스트림을 닫는다.
 *
 * <p>정상 경로에서는 본문 실행이 시작되면 {@link Handle#claim()} 으로 기록에서 빠진다 — 전송 중인 스트림을 다른 스레드가 닫지 않게.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PendingDownloadStreams {

    static final String ATTRIBUTE = PendingDownloadStreams.class.getName();

    /** 기록된 스트림 하나. 본문이 실행을 시작하면 claim 해서 거절 정리 대상에서 뺀다 */
    public static final class Handle {
        private final Closeable stream;
        private boolean claimed;

        private Handle(Closeable stream) {
            this.stream = stream;
        }

        /** 본문이 스트림을 맡았다 — 이후 정리는 본문 책임 */
        public synchronized void claim() {
            claimed = true;
        }

        private synchronized void closeIfUnclaimed() {
            if (claimed) {
                return;
            }
            claimed = true;
            try {
                // S3 ResponseInputStream: Apache 클라이언트에서 close() 는 연결 재사용을 위해 남은 본문을 끝까지 읽는다.
                // 아직 한 바이트도 보내지 않은 응답이라 abort() 로 연결을 바로 끊는다(대용량 파일을 503 전에 다 읽지 않게)
                if (stream instanceof Abortable abortable) {
                    abortable.abort();
                }
                stream.close();
            } catch (IOException | RuntimeException e) {
                log.debug("Failed to close pending download stream", e);
            }
        }
    }

    /** 현재 요청(있으면)에 스트림을 기록한다. 요청 밖(배치 등)에서는 기록만 생략하고 핸들을 돌려준다 */
    public static Handle register(Closeable stream) {
        Handle handle = new Handle(stream);
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            handles(attributes, true).add(handle);
        }
        return handle;
    }

    /** 이 요청이 연 스트림 중 본문이 맡지 않은 것을 모두 닫는다 */
    public static void closeAll(RequestAttributes attributes) {
        if (attributes == null) {
            return;
        }
        List<Handle> handles = handles(attributes, false);
        if (handles == null) {
            return;
        }
        attributes.removeAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        handles.forEach(Handle::closeIfUnclaimed);
    }

    @SuppressWarnings("unchecked")
    private static List<Handle> handles(RequestAttributes attributes, boolean create) {
        Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value == null && create) {
            List<Handle> list = new ArrayList<>();
            attributes.setAttribute(ATTRIBUTE, list, RequestAttributes.SCOPE_REQUEST);
            return list;
        }
        return (List<Handle>) value;
    }
}
