package com.thinkfree.drive.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiExceptionResponse {
    
    private final boolean result;
    private final int code;
    private final String errorCode;
    private final String message;
    private final Object errors;
    
    public static ApiExceptionResponse of(HttpStatus status, String errorCode, String msg){
        return new ApiExceptionResponse(false, status.value(), errorCode, msg, null);
    }
    
    public static ApiExceptionResponse of(HttpStatus status, String msg){
        return ApiExceptionResponse.of(status, null, msg);
    }

    public static ApiExceptionResponse of(ErrorCode errorCode){
        return ApiExceptionResponse.of(errorCode.getStatus(), errorCode.getErrorCode(), errorCode.getMessage());
    }

    public static ResponseEntity<ApiExceptionResponse> error(HttpStatus status){
        return ApiExceptionResponse.error(status, status.getReasonPhrase());
    }

    public static ResponseEntity<ApiExceptionResponse> error(HttpStatus status, String msg){
        return ApiExceptionResponse.error(status, null, msg, null);
    }
    public static ResponseEntity<ApiExceptionResponse> error(ErrorCode errorCode, String msg){
        return ApiExceptionResponse.error(errorCode.getStatus(), errorCode.getErrorCode(), msg, null);
    }
    
    public static ResponseEntity<ApiExceptionResponse> error(ErrorCode errorCode){
        return ApiExceptionResponse.error(errorCode.getStatus(), errorCode.getErrorCode(), errorCode.getMessage(), null);
    }

    public static ResponseEntity<ApiExceptionResponse> error(HttpStatus status, String errorCode, String msg, Object errors){
        return ResponseEntity.status(status)
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(new ApiExceptionResponse(false, status.value(), errorCode, msg, errors));
    }
    

}
