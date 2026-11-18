package com.thinkfree.drive.oauth2.response;

/** 동의 화면 표시 데이터 — 프론트(pages/oauth2-consent)가 client_id로 조회 */
public record OAuth2ConsentInfoResponse(
        String clientName
) {
}
