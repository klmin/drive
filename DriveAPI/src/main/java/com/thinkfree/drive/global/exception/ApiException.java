package com.thinkfree.drive.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public class ApiException extends RuntimeException {

	private final HttpStatus	status;
	private final String		message;

	public ApiException(HttpStatus status, String message, Throwable e) {
		super(e);
		this.status = status;
		this.message = message;
	}
	public ApiException(HttpStatus status) {
		super();
		this.status = status;
        this.message = status.getReasonPhrase();
    }

	public ApiException(String message) {
		super();
		this.status = HttpStatus.BAD_REQUEST;
		this.message = message;
	}

}
