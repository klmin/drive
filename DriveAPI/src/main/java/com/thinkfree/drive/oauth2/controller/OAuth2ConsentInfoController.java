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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 동의 화면 데이터 제공 — 커스텀 동의 페이지(DriveFront pages/oauth2-consent)가
 * client_id로 클라이언트 표시명과 redirect 목적지를 조회한다. 요청하는 scope 목록은 SAS가 동의
 * 리다이렉트 쿼리로 이미 전달하므로 여기서는 보강하지 않는다.
 *
 * 익명 DCR 이라 표시명은 누구나 아무 값으로 정할 수 있다 — 동의 화면이 유일한 사용자 확인 지점이므로
 * 코드가 실제로 전달될 redirect 목적지를 함께 내려 사칭(이름은 Claude, 목적지는 공격자 서버)을 드러낸다.
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
        return ResponseEntity.ok(new OAuth2ConsentInfoResponse(client.getClientName(), redirectHosts(client.getRedirectUris())));
    }

    /**
     * redirect_uri → 표시용 목적지. https/http 는 host[:port], 그 밖의 앱 스킴은 scheme://host (host 없으면 scheme:).
     * 경로·쿼리는 사칭 판단에 필요 없고 길어서 뺀다. 정렬(결정적 표시 순서)·중복 제거.
     */
    static List<String> redirectHosts(Set<String> redirectUris) {
        Set<String> hosts = new LinkedHashSet<>();
        for (String uri : new TreeSet<>(redirectUris)) {
            try {
                URI parsed = new URI(uri);
                String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
                String host = parsed.getHost();
                String authority = host == null ? null : (parsed.getPort() == -1 ? host : host + ":" + parsed.getPort());
                if ("https".equals(scheme) || "http".equals(scheme)) {
                    hosts.add(authority == null ? uri : authority);
                } else {
                    hosts.add(authority == null ? scheme + ":" : scheme + "://" + authority);
                }
            } catch (URISyntaxException e) {
                hosts.add(uri);
            }
        }
        return List.copyOf(hosts);
    }
}
