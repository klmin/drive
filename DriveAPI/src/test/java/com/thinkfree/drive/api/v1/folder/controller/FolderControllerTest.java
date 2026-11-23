package com.thinkfree.drive.api.v1.folder.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.folder.request.FolderMoveRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderRenameRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderUploadRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(userSeq = 2)
@Transactional
class FolderControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/folders");
    }
    
    @WithMockUserCustom(userSeq = 859, tenantSeq = 1)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void upload() {
        //setTestHost("test6.drive.thinkfree.com");
        Integer ss = null;
        Long pfs = null;
        String fn = "test1";

        var request = new FolderUploadRequest(ss, pfs, fn);
        
        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isCreated());
        
//        fn = "test600-4번폴더";
        request = new FolderUploadRequest(ss, pfs, fn);
        postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertEquals(HttpStatus.CREATED.value(), response.getCode());

    }

    @WithMockUserCustom(userSeq = 669)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void download() throws Exception {
        
        Long resourceSeq = 25159L;
        Integer ss = 670;
        String url = StringUtil.buildUrl(resourceSeq, "download");
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);

        var response =  mvcResultResponse(get(url), null, multiValueMap(map), true, status().isOk());
        
        byte[] zipData = response.getContentAsByteArray();
        assertNotNull(zipData, "ZIP data should not be null");

        String contentDisposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);

        assertNotNull(contentDisposition);
        assertNotNull(contentType);
        assertEquals(contentType, DriveConstants.APPLICATION_ZIP.toString());

        String extractedFileName = extractFileNameFromContentDisposition(contentDisposition);
        
        File downloadFile = new File(extractedFileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
            fileOutputStream.write(response.getContentAsByteArray());
        }
        
        assertTrue(downloadFile.exists());
        assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
        assertTrue(downloadFile.delete());

    }

    @Test
    void concurrentUpload(){
        
        Integer ss = 21;
        Long parentFolderSeq = null;
        String folderName = "test-copy";

        var request = new FolderUploadRequest(ss, parentFolderSeq, folderName);

        int threadCount = 5;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();

        Executor executor = new DelegatingSecurityContextExecutor(
                Executors.newFixedThreadPool(threadCount)
        );

        for (int i = 0; i < threadCount; i++) {

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                try {

                    postForObject(request, new TypeReference<>() {}, status().isCreated());

                    successCnt.getAndIncrement();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor).exceptionally(e -> {
                failCnt.getAndIncrement();
                log.error("CompletableFuture Exception: {}", e.getMessage());
                return null;
            });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("successCnt : {}", successCnt.get());
                    log.info("failCnt : {}", failCnt.get());
                }).join();

    }

    @WithMockUserCustom(userSeq = 669)
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void delete() {

        Long resourceSeq = 25159L;
        String url = StringUtil.buildUrl(resourceSeq, "delete");

        ApiResponse<Void> apiResponse = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), apiResponse.getCode());

    }
    
    @WithMockUserCustom(userSeq = 669)
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void rename() {

        Long resourceSeq = 25159L;
        Integer ss = 670;
        String rn = "test3333";
        String url = StringUtil.buildUrl(resourceSeq, "rename");

        var request = new FolderRenameRequest(ss, rn);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {},
                status().isOk()
        );

        assertEquals(OK.value(), response.getCode());

    }

    @Test
    @WithMockUserCustom(userSeq = 914)
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void move() {
        
        // 1 : 621(ORG_LINK) -> 623 -> 636 -> 638 -> 639(ORG_LINK) -> 647 -> 648
        // 21 : 658(ORG_LINK) -> 659 -> 660 -> 661 -> 662
        
        /* 다른 소유주
        
        Long resourceSeq = 638L;
        Integer ss = 1;
        Integer ts = 21;
        Long pfs = 658L;
        */
        
        // 자기자신
        Long resourceSeq = 40101L;
        Integer ss = 859;
        Integer ts = null;
        Long pfs = null;
        String url = StringUtil.buildUrl(resourceSeq, "move");
        
        var request = new FolderMoveRequest(ss, ts, pfs);

        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>() {},
                status().isOk()
        );

        assertEquals(OK.value(), response.getCode());

    }


}
