package com.thinkfree.drive.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * 응답이 이미 나가기 시작한 뒤(커밋 후) 생긴 예외 — 다운로드 본문 전송 중 스토리지 오류·비동기 시간 초과 등.
 *
 * <p>이때 다른 처리기(ApiGlobalExceptionHandler, DefaultHandlerExceptionResolver)는 오류 응답을 쓸 수 없어
 * 예외를 삼키고 요청을 정상 종료한다. 그러면 Content-Length 보다 적게 보낸 채 연결이 살아 있어
 * 클라이언트는 나머지 바이트를 자기 타임아웃까지 기다린다(다운로드가 "멈춤"). chunked(zip) 는 끝 표시 없이 끝나 잘린 파일이 된다.
 *
 * <p>그래서 커밋 후 예외는 처리하지 않고 서블릿 컨테이너까지 올려 보낸다 — Tomcat 은 커밋된 응답에서 오류가 나면
 * 연결을 바로 닫아 클라이언트가 실패를 즉시 알게 한다. 클라이언트가 먼저 끊은 경우는 조용히 끝낸다.
 */
@Slf4j
@Component
public class CommittedResponseExceptionResolver implements HandlerExceptionResolver, Ordered {

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!response.isCommitted()) {
            return null;
        }
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            log.debug("Client disconnected during response: {} {}", request.getMethod(), request.getRequestURI());
            return new ModelAndView();
        }
        log.warn("Response failed after commit, closing connection: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.toString());
        throw new ResponseAbortedException(ex);
    }

    /** DefaultErrorAttributes(HIGHEST_PRECEDENCE)가 오류 속성을 기록한 다음, 나머지 처리기보다 먼저 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /** 커밋된 응답을 끊기 위해 컨테이너로 올려 보내는 예외 */
    public static class ResponseAbortedException extends RuntimeException {
        public ResponseAbortedException(Throwable cause) {
            super("Response failed after commit", cause);
        }
    }
}
