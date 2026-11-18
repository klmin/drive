package com.thinkfree.drive.license.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * com/hancom/weboffice/cloudofficeadmin/common/Exception/ResultCode.java
 *
 * 번호는 CloudOffice 와 공유한다. 화면은 이 번호로 문구를 정하므로 errorCode 도 번호를 그대로 쓴다.
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements ErrorCode {
    /**
     * LICENSE
     * 6999~
     */
    LICENSE(6999, "license error", HttpStatus.INTERNAL_SERVER_ERROR),
    LICENSE_FILE_NOT_EXIST(6998, "license file does not exist", HttpStatus.NOT_FOUND),
    LICENSE_FILE_NOT_VALID_FORMAT(6997, "license file in invalid format", HttpStatus.BAD_REQUEST),
    LICENSE_MANAGER_AUTHENTICATION_FAILED(6996, "license manager authentication failed", HttpStatus.BAD_REQUEST),
    LICENSE_FILE_EXPIRATION(6995, "license file is expired", HttpStatus.BAD_REQUEST),
    LICENSE_UNDER_LIMIT(6994, "license under limit", HttpStatus.BAD_REQUEST),
    LICENSE_EXCEED_LIMIT(6993, "license exceed limit", HttpStatus.BAD_REQUEST),
    LICENSE_OVER_EXCEED_LIMIT(6992, "license over exceed limit", HttpStatus.BAD_REQUEST),
    LICENSE_STATS_FILE_NOT_VALID_FORMAT(6991, "license stats file in invalid format", HttpStatus.BAD_REQUEST),

    /**
     * 온라인 인증 서버가 거절한 사유
     */
    LICENSE_DATE_INVALID(6989, "license date invalid", HttpStatus.BAD_REQUEST),
    LICENSE_NOT_STARTED(6987, "license not started", HttpStatus.BAD_REQUEST),
    LICENSE_INACTIVE(6985, "license is inactive", HttpStatus.BAD_REQUEST),

    /**
     * AUTH
     * 6899~
     */
    AUTH(6899, "auth error", HttpStatus.BAD_REQUEST),
    AUTH_SERVER_FAILURE(6895, "auth server failure", HttpStatus.BAD_REQUEST),

    /**
     * DB
     */
    DB(4999, "database failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus status;

    @Override
    public String getErrorCode() {
        return String.valueOf(code);
    }

    public static ResultCode fromCode(int code) {
        for (ResultCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown result code: " + code);
    }
}
