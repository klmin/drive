package com.thinkfree.drive.branding.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BrandingErrorCode implements ErrorCode {
    BRANDING_NOT_FOUND(HttpStatus.NOT_FOUND, "BRANDING_001", "브랜딩 설정 정보를 찾을 수 없습니다."),
    IMAGE_CONVERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BRANDING_002", "이미지 파일 변환 중 오류가 발생했습니다."),
    WHITE_LABEL_NOT_LICENSED(HttpStatus.FORBIDDEN, "BRANDING_003", "화이트 라벨 라이선스가 없어 브랜딩 설정을 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
