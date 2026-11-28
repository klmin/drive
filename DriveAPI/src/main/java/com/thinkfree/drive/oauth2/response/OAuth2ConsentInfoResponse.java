package com.thinkfree.drive.oauth2.response;

import java.util.List;

/**
 * 동의 화면 표시 데이터 — 프론트(pages/oauth2-consent)가 client_id로 조회.
 *
 * @param clientName    등록자가 스스로 정한 표시명 — 익명 DCR 이라 "Claude" 처럼 사칭할 수 있으므로 이것만으로 신뢰하면 안 된다
 * @param redirectHosts 등록된 redirect_uri 의 목적지(호스트, 루프백, 앱 스킴) — 인가 코드가 실제로 전달될 곳.
 *                      사용자가 표시명과 목적지를 비교해 사칭을 알아챌 수 있도록 함께 보여준다
 */
public record OAuth2ConsentInfoResponse(
        String clientName,
        List<String> redirectHosts
) {
}
