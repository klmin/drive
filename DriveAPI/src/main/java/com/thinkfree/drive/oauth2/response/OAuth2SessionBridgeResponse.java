package com.thinkfree.drive.oauth2.response;

/**
 * 브리지 성공 응답 — redirectUrl은 세션 RequestCache에 저장돼 있던 원본 authorize URL.
 * 프론트는 이 값을 해석하지 않고 window.location으로 그대로 이동만 한다.
 */
public record OAuth2SessionBridgeResponse(String redirectUrl) {
}
