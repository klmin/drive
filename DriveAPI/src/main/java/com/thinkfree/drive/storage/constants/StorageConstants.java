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
    /** S3 단일 CopyObject·PutObject 상한(5GiB) — 넘으면 UploadPartCopy 로 복사 */
    public static final long MAX_SINGLE_COPY_SIZE = 5L * 1024 * 1024 * 1024;
    /** S3 멀티파트 파트 수 상한 */
    public static final int MAX_PART_COUNT = 10_000;
    /** S3 비마지막 파트 최소 크기(5MiB) */
    public static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    /** UploadPartCopy 기본 파트 크기(512MiB) — 5TiB / 10,000 파트도 이 크기 안쪽이다 */
    public static final long DEFAULT_COPY_PART_SIZE = 512L * 1024 * 1024;
    /** 버전 저장이 원본 키에 올린 내용의 저장 마커(사용자 메타데이터 키, x-amz-meta- 접두사 제외·소문자) — 값 = 그 저장의 버전 seq */
    public static final String SAVE_MARKER_METADATA_KEY = "drive-version-seq";
    
}
