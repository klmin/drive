package com.thinkfree.drive.restclient.interceptor;

import com.thinkfree.drive.app.enums.AppProfile;
import com.thinkfree.drive.constants.DriveConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Slf4j
public class RestRequestInterceptor implements ClientHttpRequestInterceptor {

    private final AppProfile profile;

    public RestRequestInterceptor(final AppProfile profile){
        this.profile = profile;
    }
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte [] body, ClientHttpRequestExecution execution) throws IOException {

    	traceRequest(request, body);
    	ClientHttpResponse response = execution.execute(request, body);
        traceResponse(response);

        return response;
    }

    private void traceRequest(HttpRequest request, byte[] body){

        log.info("===========================rest request begin============================================");

        boolean jsonChk = jsonChk(request.getHeaders());

        log.info("URI          : {}", request.getURI());
        log.info("Method       : {}", request.getMethod());
        log.info("Headers      : {}", masked(request.getHeaders()));

        // 요청 본문도 응답 본문과 같이 PROD 가 아닐 때만 — 로그인 비밀번호 등 비밀값이 본문에 실린다
        if(AppProfile.PROD != profile && jsonChk) {
            log.info("Request body: {}", new String(body, StandardCharsets.UTF_8));
        }

        log.info("============================rest request end=============================================");

    }

    private void traceResponse(ClientHttpResponse response) throws IOException {

        log.info("============================rest response begin==========================================");

        boolean jsonChk = jsonChk(response.getHeaders());

        log.info("Status code  : {}", response.getStatusCode());
        log.info("Status text  : {}", response.getStatusText());
        log.info("Headers      : {}", masked(response.getHeaders()));

        if(AppProfile.PROD != profile && jsonChk) {
            log.info("Response body: {}", StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
        }

        log.info("============================rest response end============================================");
    }

    /** 인증 헤더(OIDC client secret 의 Basic 인증, Bearer 토큰, API 키, 쿠키)는 값을 가린다. */
    static HttpHeaders masked(HttpHeaders headers) {
        HttpHeaders copy = new HttpHeaders();
        headers.forEach((name, values) -> copy.put(name, isSensitive(name) ? List.of("****") : values));
        return copy;
    }

    private static boolean isSensitive(String headerName) {
        String name = headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE_HEADERS.contains(name) || name.contains("token") || name.contains("secret") || name.contains("api-key");
    }

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");

    private boolean jsonChk(HttpHeaders headers){

        if(headers.getContentType() == null){
            return false;
        }

        log.info("Content-Type : {}", headers.getContentType());
        
        return Stream.of(
                        MediaType.APPLICATION_JSON,
                        MediaType.valueOf(DriveConstants.CEPH_API_MEDIA_TYPE)
                     )
                     .anyMatch(headers.getContentType()::isCompatibleWith);

    }
    

}
