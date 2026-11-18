package com.thinkfree.drive.oauth2.controller;

import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.response.OAuth2ConsentInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동의 화면 데이터 제공 — 커스텀 동의 페이지(DriveFront pages/oauth2-consent)가
 * client_id로 클라이언트 표시명을 조회한다. 요청하는 scope 목록은 SAS가 동의
 * 리다이렉트 쿼리로 이미 전달하므로 여기서는 이름만 보강한다.
 *
 * 클라이언트명은 등록자가 스스로 정한 공개 표시 정보(구글 동의 화면도 노출)라
 * 익명 조회 허용 — 어떤 시큐리티 체인 matcher에도 안 걸리는 경로 (DCR과 동일 패턴).
 */
@RestController
@RequiredArgsConstructor
public class OAuth2ConsentInfoController {

    private final RegisteredClientRepository registeredClientRepository;

    @GetMapping(OAuth2Constants.CONSENT_INFO_PATH)
    public ResponseEntity<OAuth2ConsentInfoResponse> consentInfo(@RequestParam("client_id") String clientId) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new OAuth2ConsentInfoResponse(client.getClientName()));
    }
}
