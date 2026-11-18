package com.thinkfree.drive.sso.provider.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SsoProviderErrorCode implements ErrorCode {
    SSO_PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND, "SSO_PROVIDER_001", "SSO 공급자를 찾을수 없습니다."),
    IMMUTABLE_SSO_PROVIDER_PROTOCOL(HttpStatus.BAD_REQUEST, "SSO_PROVIDER_002", "SSO 유형을 변경할 수 없습니다."),
    ACTIVE_SSO_PROVIDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "SSO_PROVIDER_005", "해당 테넌트에 이미 활성화된 OIDC 또는 SAML 공급자가 존재합니다."),
    SUPER_ADMIN_LOGIN_ENFORCED(HttpStatus.CONFLICT, "SSO_PROVIDER_006", "슈퍼어드민이 로그인 설정을 전체 적용 중이라 변경할 수 없습니다."),
    DUPLICATE_SSO_PROVIDER_DISPLAY_NAME(HttpStatus.CONFLICT, "SSO_PROVIDER_007", "같은 범위에 동일한 표시 이름의 SSO 공급자가 이미 존재합니다.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
