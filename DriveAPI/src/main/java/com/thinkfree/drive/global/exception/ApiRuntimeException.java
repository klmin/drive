package com.thinkfree.drive.global.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ApiRuntimeException extends RuntimeException{
    private final ErrorCode code;
    private final Map<String, Object> context = new LinkedHashMap<>();

    public ApiRuntimeException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }
    public ApiRuntimeException with(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public static ApiRuntimeException type(ErrorCode code) {
        return new ApiRuntimeException(code);
    }
}
