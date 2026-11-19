package com.thinkfree.drive.restclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.app.enums.AppProfile;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class RestClientFactory {
    
    private final HttpInterfaceFactory httpInterfaceFactory;
    private final RestClientProperty restClientProperty;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    // Boot 가 관측(트레이스 구간·http.client.requests 메트릭, traceparent 전달)을 넣어 둔 Builder. 싱글톤에서 여러 번 쓰므로 매번 clone() 한다
    private final RestClient.Builder restClientBuilder;

    private ClientHttpRequestFactory createRequestFactory() {

        HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

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
        return restClientBuilder.clone()
                         .baseUrl(baseUrl)
                         .requestInterceptor(new RestRequestInterceptor(interceptorProfile()))
                         .requestFactory(createRequestFactory())
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
