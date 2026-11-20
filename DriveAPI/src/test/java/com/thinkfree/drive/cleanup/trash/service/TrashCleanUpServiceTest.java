package com.thinkfree.drive.cleanup.trash.service;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DriveIntegrationTest
@Transactional
public class TrashCleanUpServiceTest {
    @Autowired
    private TrashCleanUpService trashCleanUpService;

    @WithMockUserCustom
    @Test
    public void cleanUpResourcesByDateTest() {
        trashCleanUpService.cleanUpResourcesByDate();
    }

    @WithMockUserCustom
    @Test
    public void cleanUpResourcesByUserTest() {
        Integer userSeq = 23;
        trashCleanUpService.cleanUpResourcesByUser(userSeq);
    }
}
