package com.thinkfree.drive.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.exception.ApiExceptionResponse;
import com.thinkfree.drive.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilterResponseWriter {
    
    public static void write(HttpServletResponse response, ErrorCode errorCode, ObjectMapper objectMapper) throws IOException {
        ApiExceptionResponse body = ApiExceptionResponse.of(errorCode);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
