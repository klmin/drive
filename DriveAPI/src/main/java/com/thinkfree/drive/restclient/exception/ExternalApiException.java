package com.thinkfree.drive.restclient.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public class ExternalApiException extends RuntimeException{
    private final HttpStatus status;
    private final String message;
    
    public ExternalApiException(HttpStatus status) {
		super();
		this.status = status;
        this.message = status.getReasonPhrase();
    }
}
