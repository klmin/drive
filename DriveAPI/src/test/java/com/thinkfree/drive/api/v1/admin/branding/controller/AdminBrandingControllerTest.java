package com.thinkfree.drive.api.v1.admin.branding.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingImageRequest;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingUpsertRequest;
import com.thinkfree.drive.api.v1.admin.branding.response.BrandingSettingResponse;
import com.thinkfree.drive.branding.enums.BrandingImageSource;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom
@Slf4j
@Transactional
class AdminBrandingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/branding");
    }

    @WithMockUserCustom(tenantSeq = 3)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void find() {
        setTestHost("test5.drive.thinkfree.com");
        ApiResponse<BrandingSettingResponse> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());
        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
    }

    @WithMockUserCustom(tenantSeq = 3)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void upsertWithDefaultImages() {
        changeTestHost();
        String tabTile = "My Drive3";
        String serviceName = "My Drive3";
        Boolean useDefault = true;
        var request = new BrandingUpsertRequest(tabTile, serviceName, useDefault, null, null, null, null);
        ApiResponse<Void> response = putMultipartForObject(request, new TypeReference<>() {}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }

    @WithMockUserCustom(tenantSeq = 3)
    @Test
    //@Transactional
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void upsertWithCustomImages() {
        changeTestHost();
        String tabTile = "Thinkfree Drive5";
        String serviceName = "Thinkfree Drive5";
        Boolean useDefault = false;
        var request = new BrandingUpsertRequest(tabTile, serviceName, useDefault, null, null, null, null);
        
        ApiResponse<Void> response = putMultipartForObject(
                request,
                new TypeReference<>() {},
                new MockMultipartFile[]{
                        createMultipartFileFromClasspath("favicon", "files/favicon (1).ico", "image/vnd.microsoft.icon"),
                        createMultipartFileFromClasspath("logoIcon", "files/angellist-svgrepo-com.svg", "image/svg+xml"),
                        createMultipartFileFromClasspath("logoImage", "files/airtable-svgrepo-com.svg", "image/svg+xml"),
                        createMultipartFileFromClasspath("emailLogo", "files/search.png", "image/png")
                },
                status().isOk()
        );
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void getAdminImage_custom_found() throws Exception {
        
        for (BrandingImageType type : new BrandingImageType[]{BrandingImageType.FAVICON, BrandingImageType.LOGO_ICON, BrandingImageType.LOGO_IMAGE, BrandingImageType.EMAIL_LOGO}) {
            
            String url = StringUtil.buildUrl( "images", type.getPathValue());
            BrandingImageRequest request = new BrandingImageRequest(BrandingImageSource.CUSTOM);
            
            var response = mvcResultResponse(get(url), null, multiValueMap(request), false, status().isOk());
            
            String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
            String contentLength = response.getHeader(HttpHeaders.CONTENT_LENGTH);
            
            assertNotNull(contentType);
            assertNotNull(contentLength);
            
            File downloadFile = new File("custom-"+type.getFileName());
            try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
                fileOutputStream.write(response.getContentAsByteArray());
            }
            
            assertTrue(downloadFile.exists());
            assertEquals(String.valueOf(downloadFile.length()), contentLength);
            assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
            assertTrue(downloadFile.delete());
        }
      
    }

    @Test
    void getAdminImage_default() throws Exception {
        
        for (BrandingImageType type : new BrandingImageType[]{BrandingImageType.FAVICON, BrandingImageType.LOGO_ICON, BrandingImageType.LOGO_IMAGE, BrandingImageType.EMAIL_LOGO}) {
            
            String url = StringUtil.buildUrl( "images", type.getPathValue());
            BrandingImageRequest request = new BrandingImageRequest(BrandingImageSource.DEFAULT);
            
            var response = mvcResultResponse(get(url), null, multiValueMap(request), false, status().isOk());
            
            String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
            String contentLength = response.getHeader(HttpHeaders.CONTENT_LENGTH);
            
            assertNotNull(contentType);
            assertNotNull(contentLength);
            
            File downloadFile = new File("default-"+type.getFileName());
            try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
                fileOutputStream.write(response.getContentAsByteArray());
            }
            
            assertTrue(downloadFile.exists());
            assertEquals(String.valueOf(downloadFile.length()), contentLength);
            assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
            assertTrue(downloadFile.delete());
        }
    }
    
}
