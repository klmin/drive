package com.thinkfree.drive.apikey.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ApiKeyErrorCode implements ErrorCode {

    API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "API_KEY_001", "API Key를 찾을 수 없습니다."),
    API_KEY_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "API_KEY_002", "API Key 상태 변경에 실패했습니다."),
    API_KEY_REISSUE_FAILED(HttpStatus.BAD_REQUEST, "API_KEY_003", "API Key 재발급에 실패했습니다."),
    API_KEY_INVALID(HttpStatus.UNAUTHORIZED, "API_KEY_004", "유효하지 않은 API Key입니다."),
    // API_KEY_005 는 다중 role 전환(⑥-4) 후 발급 판정이 보유 역할 기준이라 쓰지 않는다 — 다른 용도로 재사용하지 말 것
    API_KEY_ROLE_EXCEEDS_ISSUER(HttpStatus.FORBIDDEN, "API_KEY_005", "본인보다 높은 권한의 API Key는 발급할 수 없습니다."),
    API_KEY_TENANT_MISMATCH(HttpStatus.BAD_REQUEST, "API_KEY_006", "대상 계정이 해당 테넌트에 속하지 않습니다."),
    API_KEY_TENANT_REQUIRED(HttpStatus.BAD_REQUEST, "API_KEY_007", "테넌트를 지정해야 합니다."),
    API_KEY_SCOPE_REQUIRED(HttpStatus.BAD_REQUEST, "API_KEY_008", "API Key는 scope를 지정해야 합니다."),
    // API_KEY_009 는 결번 — 관리자 키 scope 금지 규칙 폐기로 제거. 다른 용도로 재사용하지 말 것
    API_KEY_SCOPE_INVALID(HttpStatus.BAD_REQUEST, "API_KEY_010", "지원하지 않거나 허용되지 않는 scope 조합입니다."),
    API_KEY_TENANT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "API_KEY_011", "슈퍼 관리자 권한 API Key에는 테넌트를 지정할 수 없습니다."),
    API_KEY_TARGET_MUST_BE_SELF(HttpStatus.FORBIDDEN, "API_KEY_012", "같은 권한의 다른 계정에는 API Key를 발급할 수 없습니다."),
    API_KEY_SUPER_ADMIN_PATH_REQUIRED(HttpStatus.FORBIDDEN, "API_KEY_013", "슈퍼 관리자 API Key는 슈퍼 관리자 발급 API를 사용해야 합니다."),
    API_KEY_SCOPE_INSUFFICIENT(HttpStatus.FORBIDDEN, "API_KEY_014", "호출에 필요한 scope가 API Key에 없습니다."),
    API_KEY_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "API_KEY_015", "대상 계정이 여러 역할을 가지고 있어 키 역할(roleType)을 지정해야 합니다."),
    API_KEY_ROLE_NOT_HELD(HttpStatus.BAD_REQUEST, "API_KEY_016", "대상 계정이 보유하지 않은 역할의 API Key는 발급할 수 없습니다."),
    API_KEY_PROTECTED(HttpStatus.FORBIDDEN, "API_KEY_017", "보호된 API Key는 슈퍼 관리자만 변경할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
