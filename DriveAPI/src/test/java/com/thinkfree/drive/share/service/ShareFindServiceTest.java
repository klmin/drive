package com.thinkfree.drive.share.service;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DriveIntegrationTest
@Slf4j
class ShareFindServiceTest {
    
    @Autowired
    public ShareFindService shareFindService;
    
    @WithMockUserCustom
    @Test
    void findSharedUsersByResource() {
        Long resourceSeq = 4744L;
        int userSeq = 1;
        boolean isDelete = false;
        var sharedUsers = shareFindService.findSharedUsersByResource(resourceSeq, userSeq, isDelete);
        sharedUsers.forEach(shareUser -> log.info("shareUser : {}", shareUser));
    }
}