package com.thinkfree.drive.license.auth.client;

import com.thinkfree.drive.license.auth.request.LicenseAuthRequest;
import com.thinkfree.drive.license.auth.response.LicenseAuthResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 라이선스 인증 서버 클라이언트. 주소는 라이선스의 auth::{주소} 값이다.
 * 라이선스를 발급한 서비스(파트너 포탈, developers 등)가 자기 인증 서버로 중계한다.
 */
public interface LicenseAuthClient {

    @PostExchange("/api/auth/{type}")
    LicenseAuthResponse auth(@PathVariable("type") String type, @RequestBody LicenseAuthRequest request);

    /** 응답 서명을 검증할 공개키 목록 (JWK Set JSON). */
    @GetExchange("/.well-known/jwks.json")
    String jwks();
}
