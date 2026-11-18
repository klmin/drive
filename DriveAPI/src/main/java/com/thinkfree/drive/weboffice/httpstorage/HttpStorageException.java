package com.thinkfree.drive.weboffice.httpstorage;

import org.springframework.http.HttpStatus;

public class HttpStorageException extends RuntimeException {
    private final HttpStatus status;

    public HttpStorageException(HttpStatus status) {
        super(status.getReasonPhrase());
        this.status = status;
    }

    public HttpStatus status() { return status; }
    public static HttpStorageException unauthorized() { return new HttpStorageException(HttpStatus.UNAUTHORIZED); }
    public static HttpStorageException forbidden() { return new HttpStorageException(HttpStatus.FORBIDDEN); }
    public static HttpStorageException badRequest() { return new HttpStorageException(HttpStatus.BAD_REQUEST); }
}
