package com.thinkfree.drive.global.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.thinkfree.drive.util.LogSafe;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.weboffice.exception.WebOfficeException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/*
@ControllerAdvice로 예외 처리
*/
@Slf4j
@RestControllerAdvice
@Hidden // Swagger에서 인식 못하도록 처리
public class ApiGlobalExceptionHandler {

    /** 바인딩 실패 시 필드별 메시지 — 내부 타입명·변환기 메시지를 노출하지 않도록 고정 문구를 쓴다 */
    private static final String INVALID_ENUM_VALUE_MSG = "Value is not allowed.";
    private static final String INVALID_FORMAT_MSG = "Invalid format.";

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiExceptionResponse> storageExceptionHandler(StorageException e) {
        log.error("## storageExceptionHandler ##");
        log.error("StorageException Message : {}", e.getMessage());
        return ApiExceptionResponse.error(e.getCode());
    }
    
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiExceptionResponse> apiExceptionHandler(ApiException e) {
        log.error("## apiExceptionHandler ##");
        log.error("Exception Message : {}", e.getMessage());
        return ApiExceptionResponse.error(e.getStatus(), e.getMessage());
    }
    
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiExceptionResponse> externalApiExceptionHandler(ExternalApiException e){
        log.error("## externalApiExceptionHandler ##");
        log.error("ExternalApiException Message : {}", e.getMessage());
        return ApiExceptionResponse.error(e.getStatus(), e.getMessage());
    }
        
    @ExceptionHandler(ApiRuntimeException.class)
    public ResponseEntity<ApiExceptionResponse> applicationException(ApiRuntimeException e){
        ErrorCode code = e.getCode();
        log.error("# applicationException # ", e);
        if (!e.getContext().isEmpty()) {
            log.error("code : {}, status : {}, message : {}, context : {}", code.getErrorCode(), code.getStatus().value(), code.getMessage(), e.getContext());
        }
        return ApiExceptionResponse.error(code);
    }

    /**
     * 요청 데이터 Validation 전용 ExceptionHandler (@RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiExceptionResponse> methodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        log.error("# applicationException # ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.VALIDATION_ERROR, extractErrorMessage(fieldErrors));
    }

    /**
     * 요청 데이터 Validation 전용 ExceptionHandler (@ModelAttribute)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiExceptionResponse> bindException(BindException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        log.error("# bindException # ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.VALIDATION_ERROR, extractErrorMessage(fieldErrors));
    }

    private String extractErrorMessage(List<FieldError> fieldErrors) {

        List<Map<String, String>> list = fieldErrors
                .stream()
                .map(err -> Map.of(
                                "field", err.getField(),
                                "msg", err.isBindingFailure()
                                        ? bindingFailureMessage(err)
                                        : Objects.requireNonNull(err.getDefaultMessage())
                        )
                )
                .toList();
        return list.toString();
    }

    /**
     * 값 변환 실패(typeMismatch) 필드의 메시지. 기본 메시지는 "Failed to convert ... to required type 'com.thinkfree...'" 라
     * 내부 클래스 경로가 드러나므로 고정 문구로 바꾼다.
     */
    private String bindingFailureMessage(FieldError err) {
        if (err.contains(TypeMismatchException.class)) {
            Class<?> requiredType = err.unwrap(TypeMismatchException.class).getRequiredType();
            if (requiredType != null && requiredType.isEnum()) {
                return INVALID_ENUM_VALUE_MSG;
            }
        }
        return INVALID_FORMAT_MSG;
    }

    private String singleFieldErrorMessage(String field, String msg) {
        return List.of(Map.of("field", field, "msg", msg)).toString();
    }

    /**
     * 요청 본문(@RequestBody)을 읽지 못한 경우 — JSON 문법 오류, 빈 본문, 타입 불일치, 잘못된 enum·날짜 값 등.
     * 클라이언트 요청 오류이므로 400. 잘못된 enum 값이면 INVALID_ENUM, 그 외는 VALIDATION_ERROR.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiExceptionResponse> httpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("# HttpMessageNotReadableException # {}", e.getMessage());
        if (e.getCause() instanceof JsonMappingException jme) {
            String field = jsonFieldPath(jme);
            if (!field.isEmpty()) {
                return isEnumTarget(jme)
                        ? ApiExceptionResponse.error(GlobalErrorCode.INVALID_ENUM, singleFieldErrorMessage(field, INVALID_ENUM_VALUE_MSG))
                        : ApiExceptionResponse.error(GlobalErrorCode.VALIDATION_ERROR, singleFieldErrorMessage(field, INVALID_FORMAT_MSG));
            }
        }
        return ApiExceptionResponse.error(GlobalErrorCode.VALIDATION_ERROR);
    }

    private String jsonFieldPath(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private boolean isEnumTarget(JsonMappingException e) {
        if (e instanceof MismatchedInputException mie) {
            Class<?> target = mie.getTargetType();
            return target != null && target.isEnum();
        }
        if (e instanceof ValueInstantiationException vie) {
            return vie.getType() != null && vie.getType().isEnumType();
        }
        return false;
    }

    /**
     * 존재하지 않는 Endpoint 전용 ExceptionHandler
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiExceptionResponse> noHandlerFoundException(NoHandlerFoundException e) {
        log.error("# noHandlerFoundException # ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR);
    }

    /**
     * 매핑 없는 /api/** 요청 전용 ExceptionHandler. 그 외 경로는 다시 던져 /error-page 로 보낸다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiExceptionResponse> noResourceFoundException(NoResourceFoundException e, HttpServletRequest request) throws NoResourceFoundException {
        if (!request.getServletPath().startsWith("/api/")) {
            throw e;
        }
        log.warn("# noResourceFoundException # {} {}", e.getHttpMethod(), LogSafe.of(request.getServletPath()));
        return ApiExceptionResponse.error(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiExceptionResponse> MethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("# MethodArgumentTypeMismatchException # ", e);
        // 라우트는 매칭됐고 경로·쿼리 파라미터(@PathVariable·@RequestParam 등) 값만 변환 실패 → 클라이언트 요청 오류 400
        Class<?> requiredType = e.getRequiredType();
        return requiredType != null && requiredType.isEnum()
                ? ApiExceptionResponse.error(GlobalErrorCode.INVALID_ENUM, singleFieldErrorMessage(e.getName(), INVALID_ENUM_VALUE_MSG))
                : ApiExceptionResponse.error(GlobalErrorCode.VALIDATION_ERROR, singleFieldErrorMessage(e.getName(), INVALID_FORMAT_MSG));
    }

    /**
     * HTTP Request Method 오류 전용 ExceptionHandler
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiExceptionResponse> httpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("# HttpRequestMethodNotSupportedException # ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.NOT_SUPPORTED_METHOD_ERROR);
    }

    /**
     * MediaType 전용 ExceptionHandler
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiExceptionResponse> httpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.error("# HttpRequestMethodNotSupportedException # ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.NOT_SUPPORTED_MEDIA_TYPE_ERROR);
    }

    /**
     * 내부 서버 오류 전용 ExceptionHandler
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiExceptionResponse> handleAnyException(RuntimeException e) {
        log.error("Unhandled exception occurred: ", e);
        return ApiExceptionResponse.error(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(WebOfficeException.class)
    public ResponseEntity<String> handleWebOfficeException(WebOfficeException e){
        log.error("# webOfficeException : ", e);
        return ResponseEntity.status(e.getStatus())
                .body(e.getMessage());
    }
    
}