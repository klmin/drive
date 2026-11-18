package com.thinkfree.drive.license.auth.enums;

import com.thinkfree.drive.license.exception.ResultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 온라인 인증 상태.
 * <ul>
 *   <li>rejected — 인증 서버가 라이선스 자체를 거절했다. 실패 횟수를 세지 않는다</li>
 *   <li>blocked — 만료일과 상관없이 바로 사용을 막는다</li>
 *   <li>resultCode — 인증 서버가 거절할 때 보내는 코드이자, 화면에 알리는 코드</li>
 * </ul>
 * 온라인 인증 라이선스는 만료·시작 전·무효화·인증 실패 누적 모두 유예 없이 바로 막는다.
 * 오프라인 라이선스의 만료 유예(만료일 기준)는 이 상태와 상관없다.
 */
@Getter
@RequiredArgsConstructor
public enum LicenseAuthStatus {
    SUCCESS(false, false, null),
    GRACE_PERIOD(false, false, null),
    EXPIRED(true, true, ResultCode.LICENSE_DATE_INVALID),
    NOT_STARTED(true, true, ResultCode.LICENSE_NOT_STARTED),
    INACTIVE(true, true, ResultCode.LICENSE_INACTIVE),
    AUTH_EXPIRED(false, true, ResultCode.AUTH_SERVER_FAILURE);

    private final boolean rejected;
    private final boolean blocked;
    private final ResultCode resultCode;

    public static LicenseAuthStatus fromName(String name) {
        return name == null ? null : valueOf(name);
    }

    /**
     * 인증 서버가 거절한 코드에 해당하는 상태. 거절 코드가 아니면 null.
     * 6995(파일 만료)는 6989 와 같은 만료로 본다.
     */
    public static LicenseAuthStatus fromRejectedCode(String errorCode) {
        if (String.valueOf(ResultCode.LICENSE_FILE_EXPIRATION.getCode()).equals(errorCode)) {
            return EXPIRED;
        }
        for (LicenseAuthStatus status : values()) {
            if (status.rejected && status.resultCode.getErrorCode().equals(errorCode)) {
                return status;
            }
        }
        return null;
    }
}
