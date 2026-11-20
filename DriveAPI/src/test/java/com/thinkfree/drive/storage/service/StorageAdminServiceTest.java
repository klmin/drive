package com.thinkfree.drive.storage.service;

import org.junit.jupiter.api.Disabled;
import com.thinkfree.drive.common.DriveIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DriveIntegrationTest
@Slf4j
class StorageAdminServiceTest {
    
    @Autowired
    public StorageAdminService storageAdminService;
    
    @Test
    @Disabled("실제 Ceph 관리 API 를 호출한다 — Mock 으로 바꿀 때 다시 켠다")
    void getStorageCapacity(){
        var response = storageAdminService.getStorageCapacity();
        log.info("response : {}", response);
    }
    
}