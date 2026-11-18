package com.thinkfree.drive.license.dto;

import com.thinkfree.drive.license.exception.ResultCode;
import lombok.Getter;

@Getter
public class LicenseErrorResponse {
    private final int code;
    private final String error;

    public LicenseErrorResponse(ResultCode resultCode) {
        this.code = resultCode.getCode();
        this.error = resultCode.name();
    }

    public LicenseErrorResponse(int code, String error) {
        this.code = code;
        this.error = error;
    }
}