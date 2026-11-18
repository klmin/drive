package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * 프로필 이미지 업로드 검사(모든 테넌트) — 5MB 이하, PNG·JPG·GIF·WEBP 만.
 * 형식은 요청 Content-Type 과 파일 앞부분 시그니처가 모두 그 형식이어야 통과한다.
 * 다운로드 응답이 저장된 Content-Type 을 그대로 내보내므로 헤더만 믿거나 내용만 보면 이미지가 아닌 응답이 나갈 수 있다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProfileImageValidator {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    /** 시그니처를 판정하려고 앞에서 읽는 바이트 수(WEBP 가 가장 김) */
    private static final int HEADER_LENGTH = 12;

    private enum ImageFormat {
        PNG, JPEG, GIF, WEBP;

        boolean matches(byte[] header) {
            return switch (this) {
                case PNG -> startsWith(header, 0, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
                case JPEG -> startsWith(header, 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                case GIF -> startsWith(header, 0, ascii("GIF87a")) || startsWith(header, 0, ascii("GIF89a"));
                case WEBP -> startsWith(header, 0, ascii("RIFF")) && startsWith(header, 8, ascii("WEBP"));
            };
        }
    }

    /** image/jpg 는 비표준이지만 보내는 클라이언트가 있어 JPEG 로 받는다 */
    private static final Map<String, ImageFormat> CONTENT_TYPES = Map.of(
            "image/png", ImageFormat.PNG,
            "image/jpeg", ImageFormat.JPEG,
            "image/jpg", ImageFormat.JPEG,
            "image/gif", ImageFormat.GIF,
            "image/webp", ImageFormat.WEBP);

    public static void validateOrThrow(MultipartFile file) {
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw ApiRuntimeException.type(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        ImageFormat format = CONTENT_TYPES.get(normalize(file.getContentType()));
        if (format == null || !format.matches(readHeader(file))) {
            throw ApiRuntimeException.type(UserErrorCode.PROFILE_IMAGE_INVALID_FORMAT);
        }
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int paramIndex = contentType.indexOf(';');
        String mediaType = paramIndex < 0 ? contentType : contentType.substring(0, paramIndex);
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw ApiRuntimeException.type(UserErrorCode.PROFILE_UPLOAD_FAIL);
        }
    }

    private static boolean startsWith(byte[] header, int offset, byte[] signature) {
        return header.length >= offset + signature.length
                && Arrays.equals(header, offset, offset + signature.length, signature, 0, signature.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
