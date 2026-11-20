package com.thinkfree.drive.abstracts;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.tenant.addressing.TenantPathMarker;
import com.thinkfree.drive.tenant.enums.TenantResolutionMode;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@DriveIntegrationTest
@AutoConfigureMockMvc
@Slf4j
public abstract class AbstractMvcTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 실행 중 yml 의 {@code tenant.resolution-mode}. PATH 면 요청 경로 앞에 {@code /t/{slug}} 를 붙인다. */
    @Autowired
    protected TenantProperty tenantProperty;

    /** PATH 모드에서 슬러그 존재 확인용 — 없는 슬러그는 접두를 붙이지 않아 SUBDOMAIN 의 "모르는 host → fallback" 과 같은 의미를 유지한다. */
    @Autowired
    protected TenantFindService tenantFindService;

    protected static final HttpStatus OK = HttpStatus.OK;
    protected static final HttpStatus CREATED = HttpStatus.CREATED;
    protected static final HttpStatus BAD_REQUEST = HttpStatus.BAD_REQUEST;
    protected static final HttpStatus INTERNAL_SERVER_ERROR = HttpStatus.INTERNAL_SERVER_ERROR;
    protected static final HttpStatus CONFLICT = HttpStatus.CONFLICT;
    private static String baseUrl = "";

    // ============================================================
    // 멀티테넌트 테스트 대상 테넌트 선언
    //   테스트는 "어느 테넌트인가"만 선언하고, 요청에 Host 를 실을지 /t/{slug} 접두를 붙일지는
    //   yml tenant.resolution-mode 에 따라 베이스가 정한다. 선언 방법 4가지가 모두 (testHost, testSlug) 두 필드를 채운다.
    //   - setTestTenant(slug)   : 주 선언(인스턴스). TB_TENANT.SUBDOMAIN 으로 행을 찾아 Host=DOMAIN, 접두=slug. 없는 슬러그는 즉시 실패.
    //   - setTestSlug(slug)     : 주 선언의 static 판(@BeforeAll static 용). 슬러그만 저장하고 Host(DOMAIN)·존재 확인은
    //                             첫 요청 때 findBySubdomain(slug) 로 지연 수행 — 없는 슬러그는 그 첫 요청에서 즉시 실패.
    //   - setTestHost(fullDomain): 호환 경로(static). 첫 요청 때 findByDomain(host) 로 슬러그를 지연 파생.
    //                             DOMAIN 에 없는 host(test-11.thinkfree.com 등)는 접두 없음 → 양 모드 모두 fallback.
    //   - setTenantLess()        : 주소로 테넌트를 지정하지 않는 요청(슈퍼어드민 등). 양 모드 모두 미매치·접두 없음 → fallback 테넌트.
    //   ⚠️ 필드가 static 이라 같은 클래스 안에서는 테스트 간 누수가 있다. 테넌트를 고정해야 하는 클래스는 @BeforeEach/@BeforeAll 에서 선언할 것.
    //      클래스 간 누수는 {@link #resetTenantDeclarationForClass()} 가 클래스마다 기본 선언으로 되돌려 차단한다.
    // ============================================================
    protected static final String DEFAULT_TEST_HOST = "default.drive.thinkfree.com";
    protected static final String DEFAULT_ADD_TEST_HOST = "default-add.drive.thinkfree.com";
    protected static final String ADMIN_TEST_HOST   = "admin.test.local";
    /** 어느 테넌트 DOMAIN 과도 일치하지 않는 중립 host. 주소로 테넌트를 지정하지 않는 요청(fallback)과 DOMAIN 없는 테넌트의 PATH 모드 요청에 쓴다. */
    protected static final String TENANT_LESS_HOST = "localhost";

    private static String testHost = DEFAULT_TEST_HOST;
    /** PATH 모드에서 경로 앞에 붙을 슬러그. null = 접두 없음. */
    private static String testSlug;
    /** false 면 testSlug 가 testHost 로부터 아직 파생되지 않은 상태(setTestHost 호환 경로). 첫 {@link #tenantSlug()} 호출에서 채운다. */
    private static boolean slugResolved;
    /** false 면 testHost 가 testSlug 로부터 아직 파생되지 않은 상태(setTestSlug static 경로). 첫 {@link #getTestHost()} 호출에서 채운다. */
    private static boolean hostResolved = true;

    /**
     * 클래스 간 static 누수 차단: 테스트 클래스가 시작될 때 선언을 기본(default 테넌트)으로 되돌린다.
     * JUnit 은 상위 클래스의 @BeforeAll 을 하위 것보다 먼저 실행하므로 하위 @BeforeAll/@BeforeEach 의 자체 선언이 그대로 우선한다.
     * (09-23 전체 스위트 재실행에서 직전 클래스가 남긴 미등록 host 를 물려받은 선언 없는 테스트가
     *  404 TENANT_004 로 흔들리는 순서 의존이 확인돼 추가 — 예: TenantModeSwitchMvcTest 직후의 ResourceControllerTest)
     */
    @BeforeAll
    static void resetTenantDeclarationForClass() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    /** 호환 경로: Host 로 테넌트를 지정한다. 슬러그는 첫 요청 때 findByDomain(host) 로 파생된다. */
    protected static void setTestHost(String host) {
        testHost = host;
        testSlug = null;
        slugResolved = false;
        hostResolved = true;
    }

    /**
     * 주 선언의 static 판: 슬러그(TB_TENANT.SUBDOMAIN)만 저장한다. {@code @BeforeAll static} 처럼 빈을 쓸 수 없는 자리용.
     * Host(그 행의 DOMAIN)와 슬러그 존재 확인은 첫 요청({@link #getTestHost()})에서 {@link #setTestTenant} 와 같은 규칙으로 수행하므로
     * 없는 슬러그는 fallback 으로 흘러가지 않고 첫 요청에서 즉시 실패한다. 결과 (host, slug) 는 {@link #setTestTenant} 와 같다.
     *
     * @throws IllegalStateException 빈 슬러그
     */
    protected static void setTestSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalStateException("setTestSlug: slug 는 필수");
        }
        testSlug = slug;
        slugResolved = true;
        hostResolved = false;
    }

    protected static void changeTestHost() {
        setTestHost(DEFAULT_ADD_TEST_HOST);
    }

    /** 주소로 테넌트를 지정하지 않는 요청(슈퍼어드민 등). 양 모드 모두 Host 미매치·접두 없음 → 서버는 fallback 테넌트를 단다. */
    protected static void setTenantLess() {
        testHost = TENANT_LESS_HOST;
        testSlug = null;
        slugResolved = true;
        hostResolved = true;
    }

    /**
     * 주 선언: 슬러그(TB_TENANT.SUBDOMAIN)로 테넌트를 지정한다.
     * Host 에는 그 행의 DOMAIN, PATH 모드 접두에는 slug 가 들어가므로 yml 모드가 어느 쪽이든 같은 테넌트로 간다.
     *
     * @throws IllegalStateException 없는 슬러그(fallback 으로 조용히 흘러가는 것을 막기 위해 즉시 실패),
     *                               SUBDOMAIN 모드인데 DOMAIN 이 비어 있어 Host 로 겨냥할 수 없는 테넌트
     */
    protected void setTestTenant(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalStateException("setTestTenant: slug 는 필수");
        }
        testHost = resolveHostFromSlug("setTestTenant", slug);
        testSlug = slug;
        slugResolved = true;
        hostResolved = true;
    }

    /**
     * 슬러그 → Host 규칙({@link #setTestTenant}·{@link #setTestSlug} 공용).
     * DOMAIN 이 있으면 그 값, 없으면 PATH 모드에서만 중립 host(서버가 Host 를 보지 않음), SUBDOMAIN 모드면 겨냥 불가로 실패.
     */
    private String resolveHostFromSlug(String caller, String slug) {
        var tenant = tenantFindService.findBySubdomain(slug);
        if (tenant == null) {
            throw new IllegalStateException(caller + ": TB_TENANT.SUBDOMAIN='" + slug + "' 인 테넌트가 없음");
        }
        if (StringUtils.hasText(tenant.getDomain())) {
            return tenant.getDomain();
        }
        if (isPathMode()) {
            return TENANT_LESS_HOST;
        }
        throw new IllegalStateException(caller + ": SUBDOMAIN 모드에서 DOMAIN 이 비어 있는 테넌트('" + slug + "')는 Host 로 겨냥할 수 없음");
    }

    /**
     * 현재 선언된 테넌트의 Host. {@link #setTestSlug} static 경로면 첫 호출 때 findBySubdomain(slug) 로 한 번 파생해 캐시한다
     * ({@link #tenantSlug()} 가 setTestHost 경로의 슬러그를 지연 파생하는 것과 대칭).
     */
    protected String getTestHost() {
        if (!hostResolved) {
            testHost = resolveHostFromSlug("setTestSlug", testSlug);
            hostResolved = true;
        }
        return testHost;
    }

    // ============================================================
    // 테넌트 주소 모드 스위치 (yml tenant.resolution-mode 를 그대로 따른다)
    //   - SUBDOMAIN : Host 헤더만 (기존 동작)
    //   - PATH      : Host 헤더 유지 + 요청 경로 앞에 /t/{slug}
    //                 slug = setTestTenant 로 직접 지정했거나, setTestHost 경로면 그 Host 가 SUBDOMAIN 모드에서
    //                 매치했을 테넌트(findByDomain(host))의 SUBDOMAIN 컬럼. 미매치는 접두 없음
    //                 → 두 모드가 같은 테넌트로 귀결된다 (매치 ⇔ 접두, 미매치 ⇔ 접두 없음 → fallback)
    //   실행 시 전환: 환경변수 TENANT_RESOLUTION_MODE=PATH|SUBDOMAIN (Spring relaxed binding, surefire fork 에 전파)
    // ============================================================
    protected boolean isPathMode() {
        return tenantProperty != null && tenantProperty.resolutionMode() == TenantResolutionMode.PATH;
    }

    /**
     * 현재 선언된 테넌트의 슬러그. {@link #setTestTenant} 로 지정했으면 그 값, {@link #setTestHost} 경로면
     * 첫 호출 때 findByDomain(host) 로 한 번 파생해 캐시한다. 미매치·SUBDOMAIN 비어 있음·tenant-less 면 null.
     */
    protected String tenantSlug() {
        if (!slugResolved) {
            testSlug = resolveSlugFromHost(testHost);
            slugResolved = true;
        }
        return testSlug;
    }

    private String resolveSlugFromHost(String host) {
        if (tenantFindService == null || !StringUtils.hasText(host)) {
            return null;
        }
        var tenant = tenantFindService.findByDomain(host);
        if (tenant == null || !StringUtils.hasText(tenant.getSubdomain())) {
            return null;
        }
        return tenant.getSubdomain();
    }

    /** PATH 모드에서 요청·절대 URL 단언에 붙는 접두({@code /t/default}). SUBDOMAIN 모드·미매치 host 면 빈 문자열. */
    protected String tenantPrefix() {
        String slug = isPathMode() ? tenantSlug() : null;
        return slug == null ? "" : TenantPathMarker.prefixOf(slug);
    }

    /** 모드에 맞춘 절대 경로(단언·직접 빌드용). 실제 요청 보정은 {@link #tenantPathRequest()} 가 한다. */
    protected String tenantUrl(String url) {
        return tenantPrefix() + (url == null ? "" : url);
    }

    /**
     * 요청 생성 직후 경로 보정.
     * <ul>
     *     <li>PATH 모드: requestURI 앞에 {@code /t/{slug}} (이미 붙어 있으면 그대로)</li>
     *     <li>양 모드 공통: Tomcat '/' 매핑과 동형으로 servletPath=전체 경로, pathInfo=null.
     *         MockMvc 기본은 servletPath="" / pathInfo=전체 경로라 {@code TenantPathRequestWrapper}(servletPath 에서 접두 제거)와 어긋난다.</li>
     * </ul>
     */
    protected RequestPostProcessor tenantPathRequest() {
        return request -> {
            String prefix = tenantPrefix();
            String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
            if (!prefix.isEmpty() && !uri.equals(prefix) && !uri.startsWith(prefix + "/")) {
                uri = prefix + uri;
                request.setRequestURI(uri);
            }
            request.setServletPath(uri);
            request.setPathInfo(null);
            return request;
        };
    }

    protected static void initBaseUrl(String url) {
        baseUrl = url;
    }
    
    protected ResultActions mockMvcPerform(RequestBuilder requestBuilder) {
        try {
            return mockMvc.perform(requestBuilder).andDo(print());
        }catch (Exception e){
            throw new RuntimeException("mockMvcPerform failed", e);
        }
    }
    
    protected <T> T readValue(String content, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(content, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("readValue failed", e);
        }
    }
    
    protected String writeValueAsString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        }catch (JsonProcessingException e){
            throw new RuntimeException("writeValueAsString failed", e);
        }
    }
    
    public ResultActions mvcPerform(MockHttpServletRequestBuilder requestBuilder, boolean isAsync) {
        try {
            ResultActions resultActions = this.mockMvc.perform(requestBuilder.locale(LocaleContextHolder.getLocale()))
                                                      .andDo(print());
            
            return isAsync ?
                        resultActions.andExpect(request().asyncStarted()).andDo(MvcResult::getAsyncResult) :
                        resultActions;

        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
        
    public MvcResult performRequestJsonAndResultMatcher(MockHttpServletRequestBuilder requestBuilder, String content, MultiValueMap<String, String> params, boolean isAsync, ResultMatcher... matchers)  {
    
        String str = requestBuilder.buildRequest(null).getContentType();
        
        if(str == null){
            requestBuilder.contentType(MediaType.APPLICATION_JSON);
        }

        if (content != null) {
            requestBuilder.content(content);
        }
        if (params != null) {
            requestBuilder.params(params);
        }
        
        try {
            
            ResultActions resultActions = mvcPerform(requestBuilder, isAsync);

            for (ResultMatcher matcher : matchers) {
                resultActions.andExpect(matcher);
            }
            
            return resultActions.andReturn();
        }catch(Exception e){
            throw new RuntimeException(e);
        }

    }
    
    public MvcResult mvcResult(MockHttpServletRequestBuilder requestBuilder, String content, MultiValueMap<String, String> params, boolean isAsync, ResultMatcher... matchers) {
        return performRequestJsonAndResultMatcher(requestBuilder, content, params, isAsync, matchers);
    }

    public MockHttpServletResponse mvcResultResponse(MockHttpServletRequestBuilder requestBuilder, String content, MultiValueMap<String, String> params, boolean isAsync, ResultMatcher... matchers)  {
        return mvcResult(requestBuilder, content, params, isAsync, matchers).getResponse();
    }

    public String contentAsString(MockHttpServletRequestBuilder requestBuilder, String content, MultiValueMap<String, String> params, ResultMatcher... matchers) {
        try{
            return mvcResultResponse(requestBuilder, content, params, false, matchers).getContentAsString();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    public <T> T response(MockHttpServletRequestBuilder requestBuilder, String content, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers)  {
        String responseBody = contentAsString(requestBuilder, content, params, matchers);
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        return readValue(responseBody, typeReference);
    }
    
    public <T> T response(MockHttpServletRequestBuilder requestBuilder, Object obj, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers)  {
        return readValue(contentAsString(requestBuilder, writeValueAsString(obj), params, matchers), typeReference);
    }

    public <T> T getForObject(String url, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(get(url), null, multiValueMap(obj), typeReference, matchers);
    }
    
    public <T> T getForObjectWithMultiParams(String url, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(get(url), null, params, typeReference,  matchers);
    }

    public <T> T postForObject(String url, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(url), writeValueAsString(obj), null, typeReference, matchers);
    }
    public <T> T postForObject(String url, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(url), null, null, typeReference, matchers);
    }
    public <T> T putForObject(String url, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(put(url), writeValueAsString(obj), null, typeReference, matchers);
    }
    public <T> T postForObject(Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(), writeValueAsString(obj), null, typeReference, matchers);
    }
    public <T> T postForObject(MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(), null, params, typeReference, matchers);
    }
    public <T> T postForObject(Object obj, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(), writeValueAsString(obj), params, typeReference, matchers);
    }
    public <T> T postForObject(String url, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(post(url), null, params, typeReference, matchers);
    }
    public <T> T patchForObject(String url, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(patch(url), writeValueAsString(obj), null, typeReference, matchers);
    }
    public <T> T patchForObject(String url, Object obj, MultiValueMap<String, String> params, TypeReference<T> typeReference, ResultMatcher... matchers) {
        var body = obj == null ? null : writeValueAsString(obj);
        return response(patch(url), body, params, typeReference, matchers);
    }
    
    public <T> T multipartForObject(String url, MockMultipartFile file, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers){
        return multipartForObject(url, file, HttpMethod.POST, obj, typeReference, matchers);
    }
    
    public <T> T multipartForObject(MockMultipartFile file,  Object obj, TypeReference<T> typeReference, ResultMatcher... matchers){
        return multipartForObject(null, file, obj, typeReference, matchers);
    }
    
    public <T> T multipartForObject(String url, MockMultipartFile file, HttpMethod httpMethod, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers){
        var buildUrl = StringUtil.buildUrl(baseUrl, url);
        var builder = withHost(MockMvcRequestBuilders.multipart(httpMethod, buildUrl));

        if (file != null) {
            builder.file(file);
        }
        builder.contentType(MediaType.MULTIPART_FORM_DATA);
        return response(builder, null, multiValueMap(obj), typeReference, matchers);
    }

    public <T> T putMultipartForObject(Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return putMultipartForObject(obj, typeReference, new MockMultipartFile[0], matchers);
    }

    public <T> T putMultipartForObject(Object obj, TypeReference<T> typeReference, MockMultipartFile[] files, ResultMatcher... matchers) {
        var builder = withHost(MockMvcRequestBuilders.multipart(HttpMethod.PUT, baseUrl));
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        builder.contentType(MediaType.MULTIPART_FORM_DATA);
        return response(builder, null, multiValueMap(obj), typeReference, matchers);
    }

    public <T> T deleteForObject(String url, Object obj, TypeReference<T> typeReference, ResultMatcher... matchers) {
        return response(delete(url), null, multiValueMap(obj), typeReference, matchers);
    }

    /**
     * 모든 request builder 에 Host 헤더 주입(SUBDOMAIN 모드 판별용) + 테넌트 경로 보정.
     * 헬퍼(get/post…)를 우회해 {@code MockMvcRequestBuilders} 를 직접 쓴 테스트도 이 메서드를 거치므로 접두 누락이 없다.
     */
    protected MockHttpServletRequestBuilder withHost(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.HOST, getTestHost()).with(tenantPathRequest());
    }

    protected MockMultipartHttpServletRequestBuilder withHost(MockMultipartHttpServletRequestBuilder builder) {
        builder.header(HttpHeaders.HOST, getTestHost()).with(tenantPathRequest());
        return builder;
    }

    protected MockHttpServletRequestBuilder get(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.get(buildUrl));
    }
    protected MockHttpServletRequestBuilder get(){
        return withHost(MockMvcRequestBuilders.get(baseUrl));
    }
    protected MockHttpServletRequestBuilder post(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.post(buildUrl));
    }
    protected MockHttpServletRequestBuilder post(){
        return withHost(MockMvcRequestBuilders.post(baseUrl));
    }
    protected MockHttpServletRequestBuilder patch(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.patch(buildUrl));
    }
    protected MockMultipartHttpServletRequestBuilder multipart(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.multipart(buildUrl));
    }
    protected MockMultipartHttpServletRequestBuilder multipart(){
        return withHost(MockMvcRequestBuilders.multipart(baseUrl));
    }
    protected MockHttpServletRequestBuilder put(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.put(buildUrl));
    }
    protected MockHttpServletRequestBuilder delete(String url){
        String buildUrl = StringUtil.buildUrl(baseUrl, url);
        return withHost(MockMvcRequestBuilders.delete(buildUrl));
    }
    
    @SafeVarargs
    protected final <T> MultiValueMap<String, String> multiValueMap(T... obj){
        
        if(obj == null){
            return null;
        }
        
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        
        for(T t : obj){
            Map<String, String> editMap = objectMapper.convertValue(t, new TypeReference<>() {});
            multiValueMap.setAll(editMap);
        }
        
        return multiValueMap;
    }
    
    protected <T> MultiValueMap<String, String> multiValueMap(T obj){
    
        if(obj == null){
            return null;
        }

        Map<String, String> editMap = objectMapper.convertValue(obj, new TypeReference<>() {});

        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.setAll(editMap);
        return multiValueMap;
    }
    
    protected MockMultipartFile createMockMultipartFile(String path, String contentType){
        try {
            var resource = new ClassPathResource(path);
            var file = resource.getFile();
            var fileBytes = Files.readAllBytes(file.toPath());
            
            return new MockMultipartFile(
                    "file",
                    file.getName(),
                    contentType,
                    fileBytes
            );
        }catch (Exception e){
            throw new RuntimeException("파일 생성 오류");
        }
    }
    
    protected String extractFileNameFromContentDisposition(String contentDisposition) {
    
        String prefix = "filename*=UTF-8''";

        int startIndex = contentDisposition.indexOf(prefix) + prefix.length();
        String encodedFileName = contentDisposition.substring(startIndex);

        try {
            return URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decode file name: {}", encodedFileName, e);
            return encodedFileName;
        }
    }
    
    protected MockMultipartFile createMultipartFileFromClasspath(String partName, String classpathPath, String contentType) {
        try {
            var resource = new ClassPathResource(classpathPath);
            byte[] bytes = Files.readAllBytes(resource.getFile().toPath());
            return new MockMultipartFile(partName, resource.getFilename(), contentType, bytes);
        } catch (Exception e) {
            throw new RuntimeException("이미지 파트 생성 실패: " + classpathPath, e);
        }
    }
    
 
}
