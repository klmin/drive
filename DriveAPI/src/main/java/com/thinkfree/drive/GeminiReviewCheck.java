package com.thinkfree.drive;

import java.util.Map;

/**
 * Gemini Code Assist 자동 리뷰 동작 확인용 임시 클래스 (merge 하지 않음).
 */
public class GeminiReviewCheck {

    public int parseSize(Map<String, String> headers) {
        String value = headers.get("Content-Length");
        return Integer.parseInt(value.trim());
    }
}
