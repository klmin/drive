package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.thinkfree.drive.common.DriveIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@DriveIntegrationTest
@Slf4j
@Disabled
@WithMockUserCustom
class DefaultApiKeyServiceTest {
    
    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    @Transactional
    void updateLastUsedDate() {
        String apiKey = "hNVqJPQpKcN_P8UDr9ZkjocC9H4kSSI_TRwkJxgYiMM";
        apiKeyService.updateLastUsedDate(apiKey);
    }
}