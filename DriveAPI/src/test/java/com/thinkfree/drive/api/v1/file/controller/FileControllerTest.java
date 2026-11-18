package com.thinkfree.drive.api.v1.file.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.file.request.FileCopyRequest;
import com.thinkfree.drive.api.v1.file.request.FileMoveRequest;
import com.thinkfree.drive.api.v1.file.request.FileRenameRequest;
import com.thinkfree.drive.api.v1.file.request.FileUploadRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

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
@Disabled
@WithMockUserCustom(userSeq=2)
class FileControllerTest extends AbstractMvcTest {
   
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/files");
        setTestSlug("default");
        
    }
    
    @WithMockUserCustom(userSeq=859, tenantSeq = 1)
    @Test
    void download() throws Exception {
        
        Long resourceSeq = 99039043L;
        Integer ss = null;
        String url = StringUtil.buildUrl(resourceSeq, "download");
        
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        
        var response = mvcResultResponse(get(url), null, multiValueMap(map), true, status().isOk());
        
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
    
    @WithMockUserCustom(userSeq=669)
    @Test
    void view() throws Exception {
        
        Long resourceSeq = 25187L;
        Integer ss = 670;
        String url = StringUtil.buildUrl(resourceSeq, "view");
        
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);
        
        var response = mvcResultResponse(get(url), null, multiValueMap(map), true, status().isOk());
        
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
    
    @WithMockUserCustom(userSeq=859, tenantSeq = 1)
    @Test
    //@Transactional
    void upload() throws Exception {
        
        //var resource = new ClassPathResource("files/new.tfi");
        //var resource = new ClassPathResource("files/앞뒤공백.pdf");
        var resource = new ClassPathResource("files/11112.docx");
        //var resource = new ClassPathResource("files/NewDoc.xlsx");
        var file = resource.getFile();
        var fileBytes = Files.readAllBytes(file.toPath());
        
        var multipartFile = new MockMultipartFile(
                "file",
                //resource.getFilename(),
                "11112.docx",
                //"application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ////application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
                fileBytes
        );

        Integer ss = null;
        Long rs = null;
        Long pfs = null;
        ResourceSaveType st = ResourceSaveType.DUPLICATE;
        
        var request = new FileUploadRequest(ss, pfs, rs,null, st);
        
        ApiResponse<Void> response = multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
        multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
        
//        multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
//
//        multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
        
        assertEquals(CREATED.value(), response.getCode());
        
        Thread.sleep(3000);
        
    }
    
    @WithMockUserCustom(userSeq=1)
    @Test
    void concurrentUpload() throws Exception {
        
        var resource = new ClassPathResource("files/11112.docx");
        var file = resource.getFile();
        var fileBytes = Files.readAllBytes(file.toPath());
        
        var multipartFile = new MockMultipartFile(
                        "file",
                        //resource.getFilename(),
                        "test223.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        fileBytes
                );

        Long parentFolderSeq = 5270L;
        ResourceSaveType resourceSaveType = null;
        
        var request = new FileUploadRequest(1, parentFolderSeq, null, null, resourceSaveType);
        
        MultiValueMap<String, String> multiValueMap = multiValueMap(request);

        int threadCount = 20;
      
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        
        Executor executor = new DelegatingSecurityContextExecutor(
                Executors.newFixedThreadPool(threadCount)
        );
        
        for (int i = 0; i < threadCount; i++) {
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                
            try {
                
                    mockMvcPerform(multipart()
                               .file(multipartFile)
                               .params(multiValueMap)
                               .contentType(MediaType.MULTIPART_FORM_DATA)
                        
                       )
                       .andExpect(status().isCreated());
                    
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
    
    @WithMockUserCustom(userSeq=859)
    @Test
    //@Transactional
    void delete(){
        
        Long resourceSeq = 40052L;
        Integer ss = null;
        String url = StringUtil.buildUrl(resourceSeq, "delete");
        
        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());
        
        assertEquals(OK.value(), response.getCode());
        
    }
    
    @WithMockUserCustom(userSeq=669)
    @Test
    //@Transactional
    void rename() {
        
        Long resourceSeq = 25292L;
        String rn = "문서111 (1) (4).docx";
        Integer ss = null;
        NameConflictPolicy ncp = NameConflictPolicy.AUTO;
        String url = StringUtil.buildUrl(resourceSeq, "rename");
        var request = new FileRenameRequest(ss, rn, ncp);
        
        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {},
                status().isOk()
        );
        
        assertEquals(OK.value(), response.getCode());
        
    }
    
    @WithMockUserCustom(userSeq=914)
    @Test
    @Transactional
    void move() {
        //21 - 394(ORG_LINK) ->
        //1 - > 367(ORG_LINK) -> 656(version), 613(no version)
        // 새 스프레드시트 (2) (1).xlsx
//        Long resourceSeq = 22900L;
//        Integer ss = null;
//        Integer ts = 21;
//        Long pfs = 19402L;
        Long resourceSeq = 40109L;
        Integer ss = 859;
        Integer ts = null;
        Long pfs = null;
        String url = StringUtil.buildUrl(resourceSeq, "move");
        
        var request = new FileMoveRequest(ss, ts, pfs);
        
        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>() {},
                status().isOk()
        );
        
        assertEquals(OK.value(), response.getCode());
        
    }

    
    @WithMockUserCustom(userSeq=21)
    @Test
    @Transactional
    void copy(){

        Long resourceSeq = 25247L;
        Integer ss = null;
        Integer ts = null;
        Long pfs = null;
        FileCopyRequest request = new FileCopyRequest(ss, ts, pfs);
        String url = StringUtil.buildUrl(resourceSeq, "copy");

        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>() {}, status().isCreated());
        assertEquals(CREATED.value(), response.getCode());
    }
    
    private void performDuplicateFileUpload(MockMultipartFile multipartFile, Long pfs, Long rs){
        FileUploadRequest request = new FileUploadRequest(null, pfs, rs, null, null);
        multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
        multipartForObject(multipartFile, request, new TypeReference<>() {}, status().isCreated());
    }
    
}