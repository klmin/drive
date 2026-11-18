package com.thinkfree.drive.storage.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StorageConstants {
    public static final byte[] HEALTH_PROBE_BYTES = "ok".getBytes(StandardCharsets.UTF_8);
    public static final String HEALTH_PROBE_KEY = "health/probe1.txt";
    public static final String HEALTH_PROBE_CONTENT_TYPE = "text/plain";
    public static final char MASK_CHAR = '*';
    public static final int ACCESS_KEY_VISIBLE_LENGTH = 4;
    public static final int MAX_DELETE_BATCH_SIZE = 1000;
    /** 버전 저장이 원본 키에 올린 내용의 저장 마커(사용자 메타데이터 키, x-amz-meta- 접두사 제외·소문자) — 값 = 그 저장의 버전 seq */
    public static final String SAVE_MARKER_METADATA_KEY = "drive-version-seq";
    
}
