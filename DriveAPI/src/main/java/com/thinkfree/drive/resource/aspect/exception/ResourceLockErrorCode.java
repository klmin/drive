package com.thinkfree.drive.resource.aspect.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResourceLockErrorCode implements ErrorCode {

    RESOURCE_LOCKED(HttpStatus.LOCKED, "RESOURCE_LOCK_ASPECT_002","리소스가 잠겨(Locked)있습니다.");

    private final HttpStatus status;

    private final String errorCode;

    private final String message;

}
