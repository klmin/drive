package com.thinkfree.drive.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thinkfree.drive.api.page.ApiPage;
import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private static final HttpStatus DEFAULT_SUCCESS_STATUS = HttpStatus.OK;
    private static final HttpStatus DEFAULT_FAIL_STATUS = HttpStatus.BAD_REQUEST;
    
    private boolean result;
    private int code;
    private String message;
    private T data;
    private Long totalCount;
    private Integer pageIndex;
    private Integer pageSize;

    private Number nextCursor;
    private Object nextCursorValue;
    private ResourceType nextCursorType;
    private Boolean hasNext;
    
    public static ResponseEntity<ApiResponse<Void>> created() {
        return success(HttpStatus.CREATED);
    }
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return success(HttpStatus.CREATED, data);
    }
    
    public static ResponseEntity<ApiResponse<Void>> success() {
        return success(DEFAULT_SUCCESS_STATUS);
    }
    
    public static ResponseEntity<ApiResponse<Void>> fail() {
        return create(false, null, DEFAULT_FAIL_STATUS, null, null);
    }
    
    public static ResponseEntity<ApiResponse<Void>> success(HttpStatus status) {
        return success(status, (String)null);
    }
    public static <T> ResponseEntity<ApiResponse<T>> success(HttpStatus status, T data) {
        return create(true, status.getReasonPhrase(), status, data, null);
    }
    public static ResponseEntity<ApiResponse<Void>> success(HttpStatus status, String message) {
        return create(true, message, status, null, null);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return success(data, null);
    }
    public static <T> ResponseEntity<ApiResponse<T>> success(T data, ApiPage page) {
        return create(true, null, DEFAULT_SUCCESS_STATUS, data, page);
    }

    public static <T> ResponseEntity<ApiResponse<T>> successWithCursor(T data, CursorApiPage<?> page) {
        return createWithCursor(true, null, DEFAULT_SUCCESS_STATUS, data, page);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> create(boolean result, String message, HttpStatus status, T data, ApiPage page) {
        
        var builder = ApiResponse.<T>builder()
                                 .result(result)
                                 .code(status.value())
                                 .message(Optional.ofNullable(message).orElse(status.getReasonPhrase()))
                                 .data(data);
        
        Optional.ofNullable(page).ifPresent(p -> builder
                .totalCount(p.getTotalCount())
                .pageIndex(p.getPageIndex())
                .pageSize(p.getPageSize())
        );
        
        return ResponseEntity.status(status).body(builder.build());
    }

    public static <T> ResponseEntity<ApiResponse<T>> createWithCursor(boolean result, String message, HttpStatus status, T data, CursorApiPage<?> page) {

        var builder = ApiResponse.<T>builder()
                .result(result)
                .code(status.value())
                .message(Optional.ofNullable(message).orElse(status.getReasonPhrase()))
                .data(data);

        Optional.ofNullable(page).ifPresent(p -> builder
                .pageSize(p.getPageSize())
                .nextCursor(p.getNextCursor())
                .nextCursorValue(p.getNextCursorValue())
                .nextCursorType(p.getNextCursorType())
                .hasNext(p.isHasNext())
        );



        return ResponseEntity.status(status).body(builder.build());
    }

    public static ResponseEntity<ApiResponse<Void>> fail(ErrorCode errorCode) {
        return create(
                false,
                errorCode.getMessage(),
                errorCode.getStatus(),
                null,
                null
        );
    }
    
}
