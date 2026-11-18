package com.thinkfree.drive.quota.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuotaErrorCode implements ErrorCode {
    UPDATE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "QUOTA_001", "용량 업데이트에 실패했습니다."),
    DECREASE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "QUOTA_002", "용량 감소에 실패했습니다."),
    IS_FULL(HttpStatus.BAD_REQUEST, "QUOTA_003", "용량이 부족합니다."),
    STORAGE_CAPACITY_INSUFFICIENT(HttpStatus.BAD_REQUEST, "QUOTA_004", "스토리지 가용 용량이 부족합니다."),
    UNLIMITED_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "QUOTA_005", "현재 스토리지에서는 무제한 쿼터를 지원하지 않습니다."),
    // 테넌트 총 용량 < 사용자 할당 합 — 사유는 코드로 구분하고 안내 문구는 정책 문구 하나로 통일(QUOTA_005 는 무제한 폐지로 사용처 없음)
    TENANT_QUOTA_BELOW_ALLOCATED(HttpStatus.BAD_REQUEST, "QUOTA_006", "사용 가능한 스토리지 용량을 초과했습니다. 할당량과 현재 사용량을 확인해 주세요."),
    // 사용자 할당량 합(신규·증가분 포함) > 테넌트 총 용량 — 안내 문구는 정책 문구로 통일
    TENANT_QUOTA_EXCEEDED(HttpStatus.BAD_REQUEST, "QUOTA_007", "사용 가능한 스토리지 용량을 초과했습니다. 할당량과 현재 사용량을 확인해 주세요."),
    // 사용자 할당량 0 이하(-1 무제한 포함) — 0 으로 저장된 기존 사용자도 수정 시 거부
    INVALID_USER_QUOTA(HttpStatus.BAD_REQUEST, "QUOTA_008", "할당 용량은 0보다 커야 합니다."),
    // 관리자 계정은 할당 용량 없음 — 0 또는 생략(null → 0)만 허용
    ADMIN_QUOTA_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "QUOTA_009", "관리자 계정에는 할당 용량을 지정할 수 없습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
