package com.thinkfree.drive.csv.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CsvException extends RuntimeException {
    
    private final HttpStatus status;
    private final Object errorValue;
    private final CsvErrorCode code;
    
    public CsvException(CsvErrorCode code) {
        super(code.getMessage());
        this.status = code.getStatus();
        this.errorValue = null;
        this.code = code;
    }
    public CsvException(CsvErrorCode code, String errorValue) {
        super(code.getMessage());
        this.status = code.getStatus();
        this.errorValue = errorValue;
        this.code = code;
    }
    
    public static CsvException of(CsvErrorCode code) {
        return new CsvException(code);
    }
    public static CsvException error(CsvErrorCode code, String errorValue){
        return new CsvException(code, errorValue);
    }
}
