package com.thinkfree.drive.api.v1.build.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.v1.build.response.BuildResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@Slf4j
@WithMockUserCustom
class BuildControllerTest extends AbstractMvcTest {
    
    @Autowired
    private BuildProperties buildProperties;
    
    @Autowired
    private Environment environment;
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/build");
    }
    
    @Test
    void info() {
       
        BuildResponse response = getForObject(null, null, new TypeReference<>() {}, status().isOk());
       
        String version = buildProperties.getVersion();
        String name = buildProperties.getName();
        String buildCurrentTime = buildProperties.get("currentTime");
        String finalName = buildProperties.get("finalName");
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length == 0 ? null : String.join(",", activeProfiles);

        LocalDateTime buildTime = DateUtil.parse(buildCurrentTime, "yyyyMMddHHmm");
        
        
        assertEquals(version, response.version());
        assertEquals(name, response.applicationName());
        assertEquals(finalName, response.buildFileName());
        assertEquals(buildTime, response.buildTime());
        assertEquals(profile, response.profile());
       
    }
}