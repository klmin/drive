package com.thinkfree.drive.storage.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;


@Getter
public class StorageException extends RuntimeException{
    private final ErrorCode code;
    
    public StorageException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }
    
    public static StorageException type(ErrorCode code) {
        return new StorageException(code);
    }
}