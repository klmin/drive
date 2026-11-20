package com.thinkfree.drive.global.exception;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.util.ResourceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ApiGlobalExceptionHandler — 실행기 포화(TaskRejectedException)는 503")
class ApiGlobalExceptionHandlerTaskRejectedTest {

    @RestController
    static class RejectingController {
        @GetMapping("/rejected")
        String rejected() {
            throw new TaskRejectedException("All download stream threads are busy");
        }
    }

    /** close 여부를 기록하는 스토리지 응답 대역 */
    static class TrackingStream extends ByteArrayInputStream {
        final AtomicBoolean closed = new AtomicBoolean();

        TrackingStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("다운로드 스트림 스레드가 모두 사용 중이면 503 RESPONSE_004")
    void taskRejectedIs503() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RejectingController())
                .setControllerAdvice(new ApiGlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/rejected"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(GlobalErrorCode.SERVICE_UNAVAILABLE.getErrorCode()));
    }

    @Test
    @DisplayName("거절되면 요청 스레드가 미리 연 스토리지 스트림을 닫는다")
    void rejectionClosesOpenedStorageStream() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TrackingStream storage = new TrackingStream(new byte[]{1, 2, 3});
        ResourceUtil.createFileStream(new FileData("application/octet-stream", storage, 3));

        new ApiGlobalExceptionHandler().handleTaskRejectedException(
                new TaskRejectedException("busy"), request, new MockHttpServletResponse());

        assertThat(storage.closed).isTrue();
    }

    @Test
    @DisplayName("본문이 실행을 시작한 스트림은 거절 정리 대상이 아니다")
    void startedBodyIsNotClosedByCleanup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TrackingStream storage = new TrackingStream(new byte[]{1, 2, 3});
        var body = ResourceUtil.createFileStream(new FileData("application/octet-stream", storage, 3));

        body.writeTo(new ByteArrayOutputStream());
        storage.closed.set(false); // 본문이 닫은 기록은 지우고, 정리가 다시 닫는지만 본다
        new ApiGlobalExceptionHandler().handleTaskRejectedException(
                new TaskRejectedException("busy"), request, new MockHttpServletResponse());

        assertThat(storage.closed).isFalse();
    }

    @Test
    @DisplayName("503 을 쓰기 전에 다운로드의 Content-Length·Content-Disposition 을 지우고 다른 헤더는 남긴다")
    void rejectionClearsDownloadHeaders() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setContentLengthLong(10_000_000L);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"big.bin\"");
        response.addHeader("Vary", "Origin");
        response.addHeader("Access-Control-Allow-Origin", "https://drive.example");

        new ApiGlobalExceptionHandler().handleTaskRejectedException(
                new TaskRejectedException("busy"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Vary")).isEqualTo("Origin");
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://drive.example");
    }
}
