package com.thinkfree.drive.global.exception;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다운로드 본문 전송 중(응답 커밋 후) 실패하면 오류를 삼키지 않고 컨테이너로 올려 연결이 끊기게 한다 —
 * 삼키면 Content-Length 보다 적게 보낸 채 연결이 살아 있어 클라이언트가 멈춘다.
 */
@DisplayName("CommittedResponseExceptionResolver — 커밋 후 예외는 연결을 끊도록 컨테이너로 올린다")
class CommittedResponseExceptionResolverTest {

    private final CommittedResponseExceptionResolver resolver = new CommittedResponseExceptionResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/files/download");

    @Test
    @DisplayName("커밋 전이면 처리하지 않는다 — 기존 처리기가 오류 응답을 쓴다")
    void notCommittedIsLeftToOtherResolvers() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolveException(request, response, null, new IllegalStateException("x"))).isNull();
    }

    @Test
    @DisplayName("커밋 후 실패는 ResponseAbortedException 으로 다시 던진다")
    void committedFailureIsRethrown() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        IllegalStateException cause = new IllegalStateException("storage read failed");

        assertThatThrownBy(() -> resolver.resolveException(request, response, null, cause))
                .isInstanceOf(CommittedResponseExceptionResolver.ResponseAbortedException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("커밋 후 비동기 시간 초과도 다시 던진다")
    void committedTimeoutIsRethrown() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        assertThatThrownBy(() -> resolver.resolveException(request, response, null, new AsyncRequestTimeoutException()))
                .isInstanceOf(CommittedResponseExceptionResolver.ResponseAbortedException.class);
    }

    @Test
    @DisplayName("클라이언트가 먼저 끊었으면 조용히 끝낸다")
    void clientDisconnectIsSwallowed() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        ModelAndView result = resolver.resolveException(request, response, null, new ClientAbortException("Broken pipe"));

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
    }
}
