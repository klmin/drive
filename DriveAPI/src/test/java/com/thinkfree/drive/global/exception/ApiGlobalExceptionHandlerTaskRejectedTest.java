package com.thinkfree.drive.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
