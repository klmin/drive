package com.thinkfree.drive.storage.properties;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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
