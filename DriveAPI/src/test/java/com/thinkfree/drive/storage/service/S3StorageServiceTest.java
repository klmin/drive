package com.thinkfree.drive.storage.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;

@SpringBootTest
class S3StorageServiceTest {
    
    @Autowired
    public S3StorageService s3StorageService;
    
    @Test
    void userTotalSize() {
    }
    
    @Test
    void upload() throws IOException {
        
        var resource = new ClassPathResource("test/test.txt");
        var file = resource.getFile();
        var fileBytes = Files.readAllBytes(file.toPath());
        
        var multipartFile = new MockMultipartFile(
                "file",
                file.getName(),
                "text/plain",
                fileBytes
        );
        
        var userSeq = 1;
        var fileSeq = 2L;
        
        var key = userSeq + "/" + fileSeq;
        
        s3StorageService.upload(multipartFile, key);
    }
    
    @Test
    void download() {
    }
    
    @Test
    void delete() {
    }
    
    @Test
    void generateKey() {
    }
    
    @Test
    void createBucketIfNotExists() {
    }
}