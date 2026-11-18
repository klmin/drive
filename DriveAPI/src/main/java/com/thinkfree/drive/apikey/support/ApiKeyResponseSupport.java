package com.thinkfree.drive.apikey.support;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.util.MaskingUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

/** 목록 응답 공통 변환 — role 별 컨버터 3개가 같은 규칙을 쓰도록 한 곳에 둔다 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiKeyResponseSupport {

    /** 목록 키 값 — 키 길이도 드러나지 않게 고정 길이 */
    private static final String MASKED_KEY = "*".repeat(20);

    /** 원본 키는 발급·재발급 응답에서만 노출된다. 목록은 고정 길이 '*', hide 면 {@link MaskingUtils#MASKED_PLACEHOLDER} */
    public static String maskApiKey(String apiKey, boolean hide) {
        if (!StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return hide ? MaskingUtils.MASKED_PLACEHOLDER : MASKED_KEY;
    }

    /** DB 는 공백 구분 문자열, API 는 배열. 관리자·슈퍼관리자 키는 빈 배열 */
    public static List<String> toScopes(String scope) {
        return ApiKeyScope.parse(scope).stream()
                .map(ApiKeyScope::getValue)
                .toList();
    }

}
