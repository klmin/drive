package com.thinkfree.drive.csv.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CsvErrorCode implements ErrorCode{
    
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "CSV_001", "CSV 형식 오류", "Invalid CSV format."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "CSV_002", "CSV 파일 사이즈 초과: 5MB 이하", "File size exceeds the 5MB limit."),
    UNSUPPORTED_ENCODING(HttpStatus.BAD_REQUEST, "CSV_003", "지원하지 않는 인코딩: UTF-8만 허용", "Unsupported file encoding. Only UTF-8 is allowed."),
    ROW_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CSV_004", "파일의 row 수가 라이선스의 seat 수 초과", "Row count exceeds available seat licenses."),
    UNSUPPORTED_COLUMN(HttpStatus.BAD_REQUEST, "CSV_005", "허용되지 않는 컬럼 존재 (템플릿 외 컬럼 추가됨)", "Unsupported column detected."),
    HEADER_MISMATCH(HttpStatus.BAD_REQUEST, "CSV_006", "헤더 불일치 (필수 헤더명 변경/누락)", "Header mismatch. Required header missing or renamed."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "CSV_007", "필수 필드값 누락", "Missing required field."),
    INVALID_VALUE(HttpStatus.BAD_REQUEST, "CSV_008", "유효하지 않은 값 (이메일 형식 위반, 비밀번호 정책 불일치, 스토리지 값 오류, 이미 등록된 계정 등)", "Invalid value."),
    DUPLICATE_ROW(HttpStatus.BAD_REQUEST, "CSV_009", "중복 row 존재", "Duplicate row detected."),
    UNKNOWN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CSV_010", "기타 (범용 메시지)", "An unexpected error occurred during CSV import. Please try again."),
    STORAGE_ALLOCATION_EXCEEDED(HttpStatus.BAD_REQUEST, "CSV_011", "총 할당 스토리지가 가용 공간을 초과함", "Total allocated storage exceeds available space. Adjust the allocation and try again.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String errorCase;
    private final String message;
        
}

