package com.thinkfree.drive.api.open.branding.controller;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@Slf4j
class BrandingImageControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/branding/images");
    }

    @ParameterizedTest
    @EnumSource(BrandingImageType.class)
    void getImage_returnsImage(BrandingImageType type) throws Exception {
        var response = mvcResultResponse(
                get(type.getPathValue()), null, null, false, status().isOk()
        );

        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
        String contentLength = response.getHeader(HttpHeaders.CONTENT_LENGTH);

        assertNotNull(contentType);
        assertNotNull(contentLength);
        assertTrue(response.getContentAsByteArray().length > 0);

        File downloadFile = new File("user-"+type.getFileName());
        try (FileOutputStream fos = new FileOutputStream(downloadFile)) {
            fos.write(response.getContentAsByteArray());
        }

        assertTrue(downloadFile.exists());
        assertEquals(String.valueOf(downloadFile.length()), contentLength);
        assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
        assertTrue(downloadFile.delete());
    }

    @ParameterizedTest
    @EnumSource(BrandingImageType.class)
    void getImage_returnsNotModified_whenETagMatches(BrandingImageType type) throws Exception{
        var firstResponse = mvcResultResponse(
                get(type.getPathValue()), null, null, false, status().isOk()
        );

        String etag = firstResponse.getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        String contentType = firstResponse.getHeader(HttpHeaders.CONTENT_TYPE);
        String contentLength = firstResponse.getHeader(HttpHeaders.CONTENT_LENGTH);

        assertNotNull(contentType);
        assertNotNull(contentLength);
        assertTrue(firstResponse.getContentAsByteArray().length > 0);

        File downloadFile = new File("etag-"+type.getFileName());
        try (FileOutputStream fos = new FileOutputStream(downloadFile)) {
            fos.write(firstResponse.getContentAsByteArray());
        }

        assertTrue(downloadFile.exists());
        assertEquals(String.valueOf(downloadFile.length()), contentLength);
        assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), firstResponse.getContentAsByteArray());
        assertTrue(downloadFile.delete());
        
        mvcResult(
                get(type.getPathValue()).header(HttpHeaders.IF_NONE_MATCH, etag),
                null, null, false, status().isNotModified()
        );
        
    }
}
