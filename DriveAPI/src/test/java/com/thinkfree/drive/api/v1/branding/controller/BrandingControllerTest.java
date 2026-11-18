package com.thinkfree.drive.api.v1.branding.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.v1.branding.response.BrandingMetaResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@Slf4j
class BrandingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/branding");
    }

    @Test
    void getConfig_returnsMetaResponse() {
        BrandingMetaResponse response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        assertNotNull(response);
        assertNotNull(response.tabTitle());
        assertNotNull(response.serviceName());
    }
}