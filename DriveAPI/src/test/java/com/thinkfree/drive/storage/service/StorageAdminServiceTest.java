package com.thinkfree.drive.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class StorageAdminServiceTest {
    
    @Autowired
    public StorageAdminService storageAdminService;
    
    @Test
    void getStorageCapacity(){
        var response = storageAdminService.getStorageCapacity();
        log.info("response : {}", response);
    }
    
}