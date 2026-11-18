package com.thinkfree.drive.auth.utils;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {}

    /**
     * UUID v7 생성: 48bit 타임스탬프(ms) + version(7) + 74bit 랜덤
     * 시간순 정렬 가능 + 충돌 확률 무시 가능 수준의 랜덤성 보장
     */
    public static String generate() {
        long timestamp = System.currentTimeMillis();
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        // rand_a: 12 bits
        long randA = ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);
        // MSB: [timestamp(48bit)][ver=7(4bit)][rand_a(12bit)]
        long msb = (timestamp << 16) | 0x7000L | randA;

        // rand_b: 62 bits from rand[2..9]
        long randB = 0L;
        for (int i = 2; i < 10; i++) {
            randB = (randB << 8) | (rand[i] & 0xFFL);
        }
        randB &= 0x3FFFFFFFFFFFFFFFL;
        // LSB: [variant=10(2bit)][rand_b(62bit)]
        long lsb = 0x8000000000000000L | randB;

        return new UUID(msb, lsb).toString();
    }
}
