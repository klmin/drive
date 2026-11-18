package com.thinkfree.drive.valid;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DriveAssert {

    public static void throwIfNotEqual(Object a, Object b, ErrorCode errorCode){
        if(!Objects.equals(a, b)){
            throw ApiRuntimeException.type(errorCode);
        }
    }

    public static void throwIfEqual(Object a, Object b, ErrorCode errorCode){
        if(Objects.equals(a, b)){
            throw ApiRuntimeException.type(errorCode);
        }
    }

    public static void throwIfZero(int n, ErrorCode errorCode) {
        if (n == 0) {
            throw ApiRuntimeException.type(errorCode);
        }
    }

    public static void throwIfTrue(boolean b, ErrorCode errorCode) {
        if (b) {
            throw ApiRuntimeException.type(errorCode);
        }
    }
    public static void throwIfTrue(boolean b, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (b) {
            throw exceptionSupplier.get();
        }
    }

    public static void throwIfFalse(boolean b, ErrorCode errorCode) {
        if (!b) {
            throw ApiRuntimeException.type(errorCode);
        }
    }
    
    public static void throwIfNull(Object obj, ErrorCode errorCode) {
       if (obj == null) {
           throw ApiRuntimeException.type(errorCode);
       }
    }
    public static void throwIfNull(Object obj, ErrorCode errorCode, Map<String, Object> context) {
       if (obj == null) {
            ApiRuntimeException ex = ApiRuntimeException.type(errorCode);
            if (context != null){
                context.forEach(ex::with);
            }
            throw ex;
       }
    }

    public static void throwIfNull(Object obj, String message) {
        if(obj == null){
            throw new IllegalArgumentException(message);
        }
    }
    public static void throwIfAnyNull(String message, Object ...obj) {
        throwIfNull(obj, message);
        if (obj.length == 0) {
            throw new IllegalArgumentException(message);
        }
        for(Object o : obj){
            throwIfNull(o, message);
        }
    }
    public static void throwIfAnyNull(ErrorCode errorCode, Object ...obj) {
        throwIfNull(obj, errorCode);
        if (obj.length == 0) {
            throw ApiRuntimeException.type(errorCode);
        }
        for(Object o : obj){
            throwIfNull(o, errorCode);
        }
    
    }
    public static void throwIfNoText(String str, String message) {
        if(!StringUtils.hasText(str)){
            throw new IllegalArgumentException(message);
        }
    }
    public static void throwIfNoText(String str, ErrorCode errorCode) {
        if(!StringUtils.hasText(str)){
            throw ApiRuntimeException.type(errorCode);
        }
    }
    public static void throwIfAnyNoText(String message, String ...str) {
        throwIfNull(str, message);
        if (str.length == 0) {
            throw new IllegalArgumentException(message);
        }
        for(String s : str){
            throwIfNoText(s, message);
        }
    }
    public static void throwIfAnyNoText(ErrorCode errorCode, String ...str) {
        if (str == null || str.length == 0) {
            throw ApiRuntimeException.type(errorCode);
        }
        for(String s : str){
            throwIfNoText(s, errorCode);
        }
    }
    public static void throwIfEmpty(List<?> list, ErrorCode errorCode) {
       if (list.isEmpty()) {
           throw ApiRuntimeException.type(errorCode);
       }
    }
    public static void throwIfNullOrEmpty(List<?> list, ErrorCode errorCode){
        throwIfNull(list, errorCode);
        throwIfEmpty(list, errorCode);
    }
    
    public static void throwIfGreaterOrEqual(int value, int threshold, ErrorCode errorCode) {
        if (value >= threshold) {
            throw ApiRuntimeException.type(errorCode);
        }
    }

}
