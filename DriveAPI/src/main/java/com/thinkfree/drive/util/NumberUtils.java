package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NumberUtils {
    
    public static long parseLongOrThrow(String value, Supplier<? extends RuntimeException> ex) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ex.get();
        }
    }

    public static int parseIntOrThrow(String value, Supplier<? extends RuntimeException> ex) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw ex.get();
        }
    }

    public static void requireRange(long v, long min, long max, Supplier<? extends RuntimeException> ex) {
        if (v < min || v > max){
            throw ex.get();
        }
    }

    public static void requireRange(int v, long min, long max, Supplier<? extends RuntimeException> ex) {
        if (v < min || v > max){
            throw ex.get();
        }
    }
}
