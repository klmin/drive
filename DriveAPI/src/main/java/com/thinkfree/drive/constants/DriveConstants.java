package com.thinkfree.drive.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DriveConstants {
    public static final String YYYY_M_D_H_MM = "yyyy. M. d. H:mm";
    public static final String YYYY_M_D = "yyyy. M. d";
    public static final String YYYY_MM_DD_HH_MM_SS_SSS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String YYYY_MM_DD = "yyyy-MM-dd";
    public static final String EMPTY_STRING = "";
    public static final String PATH_SEPARATOR = "/";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String YYYYMMDD_HHMMSS = "yyyyMMdd-HHmmss";
    public static final String HYPHEN = "-";
    public static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");
    public static final String DEFAULT_ZIP_NAME = "drive";
    public static final int DEFAULT_VERSION_NUMBER = 1;
    public static final int DEFAULT_TRASH_CLEAN_UP_INTERVAL_DAY = 30;
    /** 휴지통 비우기를 나눠 처리할 때 한 트랜잭션에서 지울 휴지통 루트 수(하위는 루트를 따라 전부) — 같은 사용자의 다른 쓰기가 사용자 행 잠금을 오래 기다리지 않게 */
    public static final int EMPTY_TRASH_ROOT_BATCH_SIZE = 50;
    public static final long UNLIMITED_QUOTA = -1L;
    /** 사용자 할당량 기본값(1GB) — 입력 없이 만들어지는 계정(SSO 가입) */
    public static final long DEFAULT_USER_QUOTA_BYTE = 1L * 1024 * 1024 * 1024;
    public static final long SIZE_LIMIT_UNLIMITED = -1L;
    public static final String BEARER = "Bearer ";
    public static final String TOKEN_SPLIT_SEPARATOR = "\\.";
    public static final String CEPH_API_MEDIA_TYPE = "application/vnd.ceph.api.v1.0+json";
    public static final int SUPER_ADMIN_USER_SEQ = 1;
    public static final Duration USER_PENDING_RETENTION = Duration.ofDays(30);
    public static final String NOTE_EXTENSION = "tfi";
    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final String API_V1_PREFIX = "/api/v1";
    /** 외부 API 경로 접두 — API Key(Bearer) 인증 필수, 무인증 경로 아님 */
    public static final String API_PUBLIC_V1_PREFIX = "/api/public/v1";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String HTTPS_SCHEME = "https";
    public static final String HTTP_SCHEME = "http";
    public static final String HTTPS_PREFIX = HTTPS_SCHEME + "://";
}
