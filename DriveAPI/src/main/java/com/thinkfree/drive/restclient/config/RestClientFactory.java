package com.thinkfree.drive.restclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.app.enums.AppProfile;
import com.thinkfree.drive.httpclient.guard.GuardedHttpClient;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.restclient.factory.HttpInterfaceFactory;
import com.thinkfree.drive.restclient.interceptor.RestRequestInterceptor;
import com.thinkfree.drive.restclient.properties.RestClientProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class RestClientFactory {
    
    private final HttpInterfaceFactory httpInterfaceFactory;
    private final RestClientProperty restClientProperty;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    // 사용자·테넌트 관리자가 입력한 주소 전용(내부망 가드 + 리다이렉트 끔) — createGuardedRestClient·guardedRestOperations
    private final GuardedHttpClient guardedHttpClient;
    // Boot 가 관측(트레이스 구간·http.client.requests 메트릭, traceparent 전달)을 넣어 둔 Builder. 싱글톤에서 여러 번 쓰므로 매번 clone() 한다
    private final RestClient.Builder restClientBuilder;

    private ClientHttpRequestFactory createRequestFactory(HttpClient client) {

        HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);

        if (!restClientProperty.traceEnabled()) {
            return httpComponentsClientHttpRequestFactory;
        }

        return new BufferingClientHttpRequestFactory(httpComponentsClientHttpRequestFactory);
    }

    // 인터셉터는 PROD 가 아니면 응답 본문까지 남긴다 — trace 꺼짐을 PROD 로 넘겨 기존 운영 동작과 같게
    private AppProfile interceptorProfile() {
        return restClientProperty.traceEnabled() ? AppProfile.LOCAL : AppProfile.PROD;
    }

    public RestClient createRestClient(String baseUrl) {
        return build(baseUrl, httpClient);
    }

    /** 사용자·테넌트 관리자가 입력한 주소(OIDC issuer 등)용 — 내부망 가드({@code drive.outbound-guard})를 거치고 리다이렉트를 따라가지 않는다. */
    public RestClient createGuardedRestClient(String baseUrl) {
        return build(baseUrl, guardedHttpClient.client());
    }

    /** {@link #createGuardedRestClient} 와 같은 가드의 RestOperations — Nimbus JWKS 조회({@code NimbusJwtDecoder...restOperations}) 용. */
    public RestOperations guardedRestOperations() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(guardedHttpClient.client()));
    }

    private RestClient build(String baseUrl, HttpClient client) {
        return restClientBuilder.clone()
                         .baseUrl(baseUrl)
                         .requestInterceptor(new RestRequestInterceptor(interceptorProfile()))
                         .requestFactory(createRequestFactory(client))
                         .messageConverters(
                                 converters -> {
                                     converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                                     converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                                 })
                         .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                         .defaultStatusHandler(
                                 HttpStatusCode::is4xxClientError,
                                 (request, response) -> {
                                     log.error("Client Error Code : {}", response.getStatusCode());
                                     log.error("Client Error Message : {}", new String(response.getBody().readAllBytes()));
                                     throw new ExternalApiException(HttpStatus.valueOf(response.getStatusCode().value()));
                                 })
                         .defaultStatusHandler(
                                 HttpStatusCode::is5xxServerError,
                                 (request, response) -> {
                                     log.error("Server Error Code : {}", response.getStatusCode());
                                     log.error("Server Error Message : {}", new String(response.getBody().readAllBytes()));
                                     throw new ExternalApiException(HttpStatus.valueOf(response.getStatusCode().value()));
                                 })
                         .build();
    }
    
    public <T> T createClient(Class<T> clazz, String url) {
        return httpInterfaceFactory.createClient(clazz, this.createRestClient(url));
    }
    public <T> T createClient(Class<T> clazz, RestClient restClient) {
        return httpInterfaceFactory.createClient(clazz, restClient);
    }
    
}
