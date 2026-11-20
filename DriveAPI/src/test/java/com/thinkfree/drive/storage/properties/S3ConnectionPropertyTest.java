package com.thinkfree.drive.storage.properties;

import com.thinkfree.drive.common.DriveIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DriveIntegrationTest
@Slf4j
class S3ConnectionPropertyTest {

    @Autowired
    public S3ConnectionProperty s3ConnectionProperty;

    @Autowired
    public StorageAdminProperty storageAdminProperty;

    @Test
    void test(){
        log.info("s3ConnectionProperty : {}", s3ConnectionProperty);
        log.info("storageAdminProperty : {}", storageAdminProperty);
    }
}
