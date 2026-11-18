package com.thinkfree.drive.api.v1.document.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Disabled
@WithMockUserCustom
class DocumentControllerTest extends AbstractMvcTest{
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/documents");
    }
    
    @Test
    @WithMockUserCustom(userSeq = 1)
    //@Transactional
    void create() throws InterruptedException {
        
        Integer ss = null;
        Long pfs = null;
        DocumentTemplateType documentTemplateType = DocumentTemplateType.NOTE;
        String tt = documentTemplateType.toString().toLowerCase();
        
        Map<String, Object> param = new HashMap<>();
        param.put("ss", ss);
        param.put("pfs", pfs);
        param.put("tt", tt);

        ApiResponse<Void> response = postForObject(param, new TypeReference<>() {}, status().isCreated());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
        Thread.sleep(2000);
//        postForObject(param, new TypeReference<>() {}, status().isCreated());
//
//        postForObject(param, new TypeReference<>() {}, status().isCreated());

    }
    
}