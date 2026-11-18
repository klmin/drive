package com.thinkfree.drive.quota.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;


@Getter
public class QuotaException extends RuntimeException{
    private final ErrorCode code;
    
    public QuotaException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }
    
}