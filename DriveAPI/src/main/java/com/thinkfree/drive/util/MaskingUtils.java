package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MaskingUtils {

    /** 응답에서 값을 숨겼다는 표시 — 화면은 이 값을 받으면 블러로 표시한다 */
    public static final String MASKED_PLACEHOLDER = "__MASKED__";

    /** 값이 있으면 {@link #MASKED_PLACEHOLDER}, 비어 있으면 그대로 */
    public static String hideIfPresent(String value) {
        return StringUtils.hasText(value) ? MASKED_PLACEHOLDER : value;
    }

    public static String maskFrom(String input, int visiblePrefix, char maskChar) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        if (visiblePrefix < 0) {
            throw new IllegalArgumentException("visiblePrefix must be >= 0");
        }
        if (visiblePrefix >= input.length()) {
            return maskAll(input, maskChar);
        }

        String visiblePart = input.substring(0, visiblePrefix);
        String maskedPart = maskCharRepeat(maskChar, input.length() - visiblePrefix);

        return visiblePart + maskedPart;
    }

    
    public static String maskAll(String input, char maskChar) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return maskCharRepeat(maskChar, input.length());
    }

    private static String maskCharRepeat(char maskChar, int count) {
        return String.valueOf(maskChar).repeat(Math.max(0, count));
    }
}
