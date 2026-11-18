package com.thinkfree.drive.weboffice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WebOfficeException extends RuntimeException{
    
    private final HttpStatus status;
    
    public WebOfficeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
