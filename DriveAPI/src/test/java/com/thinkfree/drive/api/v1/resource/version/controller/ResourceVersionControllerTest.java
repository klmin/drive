package com.thinkfree.drive.api.v1.resource.version.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceListResponse;
import com.thinkfree.drive.api.v1.resource.version.request.ResourceVersionListRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(roles = "ROLE_USER")
class ResourceVersionControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/resources");
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 2)
    @Test
    void list() {
        
        Integer pi = 1;
        Integer ps = 10;
        Long resourceSeq = 40014L;
        Integer ss = null;
        String url = StringUtil.buildUrl(resourceSeq, "versions");
        
        var request = new ResourceVersionListRequest(null, ss);
        var pagingRequest = new PagingRequest(pi, ps);

        MultiValueMap<String, String> params = multiValueMap(request, pagingRequest);
        
        ApiResponse<List<ResourceListResponse>> response = getForObjectWithMultiParams(url, params, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        assertEquals(pi, response.getPageIndex());
        Assertions.assertNotNull(response.getTotalCount());
    
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq=1)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 1 의 리소스 23768·버전 1737 없음(404)")
    @Test
    void download() throws Exception {
        
        Long resourceSeq = 23768L;
        Long resourceVersionSeq = 1737L;
        Integer ss = null;
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        
        // 4b40f6f01(TFDRIVE-1144) 이후 다운로드 = 토큰 발급(/{versionSeq}/download-token) → 토큰으로 스트리밍(/download?dt=)
        String tokenUrl = StringUtil.buildUrl(resourceSeq, "versions", resourceVersionSeq, "download-token");
        ApiResponse<DownloadLinkResponse> link = getForObject(tokenUrl, map, new TypeReference<>() {}, status().isOk());
        String dt = UriComponentsBuilder.fromUriString(link.getData().linkUrl()).build().getQueryParams().getFirst("dt");

        String url = StringUtil.buildUrl(resourceSeq, "versions", "download");
        Map<String, Object> dtParam = new HashMap<>();
        dtParam.put("dt", dt);
        var response = mvcResultResponse(get(url), null, multiValueMap(dtParam), true, status().isOk());
        
        String contentDisposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
        
        assertNotNull(contentDisposition);
        assertNotNull(contentType);
        
        String extractedFileName = extractFileNameFromContentDisposition(contentDisposition);
        
        File downloadFile = new File(extractedFileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
            fileOutputStream.write(response.getContentAsByteArray());
        }
        
        assertTrue(downloadFile.exists());
        assertEquals(String.valueOf(downloadFile.length()), response.getHeader(HttpHeaders.CONTENT_LENGTH));
        assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
        assertTrue(downloadFile.delete());
        
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq=1)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 1 의 리소스 23768·버전 1737 없음(RESOURCE_VERSION_003), 실행 시 dev 버전 복원")
    @Test
    void restoreVersion() {
        
        Long resourceSeq = 23768L;
        Long resourceVersionSeq = 1737L;
        Integer ss = null;
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        
        String url = StringUtil.buildUrl(resourceSeq, "versions", resourceVersionSeq, "restore");
        
        ApiResponse<Void> response = postForObject(url, map, new TypeReference<>() {}, status().isCreated());
        
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
        
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq=1)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 1 의 리소스 23768·버전 1738 없음(RESOURCE_VERSION_002), 실행 시 dev 버전 삭제")
    @Test
    void deleteVersion() throws InterruptedException {
        
        Long resourceSeq = 23768L;
        Long resourceVersionSeq = 1738L;
        Integer ss = null;
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        
        String url = StringUtil.buildUrl(resourceSeq, "versions", resourceVersionSeq);
        
        ApiResponse<Void> response = deleteForObject(url, map, new TypeReference<>() {}, status().isOk());
        
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
        Thread.sleep(5000);
    }

    @WithMockUserCustom(roles = "ROLE_USER", userSeq=21)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 21 의 리소스 5293 없음(RESOURCE_005), 실행 시 dev 버전 전체 삭제")
    @Test
    void deleteAllVersions() throws InterruptedException {
        Long resourceSeq = 5293L;
        Integer ss = 1;
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        String url = StringUtil.buildUrl(resourceSeq, "versions");
        ApiResponse<Void> response = deleteForObject(url, map, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());

        Thread.sleep(5000);
    }

}
