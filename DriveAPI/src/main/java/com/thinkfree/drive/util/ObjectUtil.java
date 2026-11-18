package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectUtil {
    
    public static <T> T valueIfNotNull(Object check, T value) {
        return check == null ? null : value;
    }
    
    public static <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }
    
    public static String toStringIfNotNull(Object obj){
        return obj == null ? null : obj.toString();
    }
    
    
}
