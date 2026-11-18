package com.thinkfree.drive.storage.enums;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum StorageUnit {
    
    BYTE(1L),
    KB(1024L),
    MB(1024L * 1024L),
    GB(1024L * 1024L * 1024L),
    TB(1024L * 1024L * 1024L * 1024L);

    private final long bytes;

    public long toBytes(long value) {
        try {
            return Math.multiplyExact(value, bytes);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Byte overflow: value : " + value + ", unit : " + this.name(), e);
        }
    }

}
