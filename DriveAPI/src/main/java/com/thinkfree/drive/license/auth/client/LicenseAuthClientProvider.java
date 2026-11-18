package com.thinkfree.drive.license.auth.client;

import com.thinkfree.drive.restclient.config.RestClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증 주소는 라이선스마다 다를 수 있어 주소별로 클라이언트를 한 번만 만들어 둔다.
 */
@Component
@RequiredArgsConstructor
public class LicenseAuthClientProvider {

    private final RestClientFactory restClientFactory;
    private final Map<String, LicenseAuthClient> clientCache = new ConcurrentHashMap<>();

    public LicenseAuthClient get(String baseUrl) {
        return clientCache.computeIfAbsent(baseUrl, url -> restClientFactory.createClient(LicenseAuthClient.class, url));
    }
}
