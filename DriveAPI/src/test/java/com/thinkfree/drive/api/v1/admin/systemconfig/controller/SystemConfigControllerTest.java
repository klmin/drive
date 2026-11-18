package com.thinkfree.drive.api.v1.admin.systemconfig.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.*;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.*;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom//(tenantSeq = 3)
class SystemConfigControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/system-config");
        //setTestHost("test-11.thinkfree.com");
       // setTestHost("default-add.drive.thinkfree.com");
    }
    
    @Test
    void getGeneralConfig() {
        ApiResponse<GeneralResponse> response = getForObject("general", null, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Transactional
    @Test
    void putGeneral() {
        
        String clientDomain = "https://drive-dev.local.thinkfree.com";
        var request = new GeneralUpdateRequest(clientDomain);
        ApiResponse<Void> response = putForObject("general", request, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Test
    void getOfficeConfig() {
        ApiResponse<OfficeResponse> response = getForObject("office", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Transactional
    @Test
    void putOffice() {
        
        String officeDomain = "https://drive-tfo.local.thinkfree.com";
        String officeAdapterName = "new-drive-dev";
        var request = new OfficeUpdateRequest(officeDomain, "ticket", "test-office-service-token", "test-office-storage-key-minimum-32-bytes");
        ApiResponse<Void> response = putForObject("office", request, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Test
    void getStorageInfo() {
        
        ApiResponse<StorageResponse> response = getForObject("storage", null, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
        
    }
    
    @Test
    void getIntellectInfo() {
        ApiResponse<IntellectResponse> response = getForObject("intellect", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Test
    //@Transactional
    void putIntellect() {
        var request = new IntellectUpdateRequest(true);
        ApiResponse<Void> response = putForObject("intellect", request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void getFileSizeLimit() {
        ApiResponse<FileSizeLimitResponse> response = getForObject("file-size-limit", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response);
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    //@Transactional
    @Test
    void putFileSizeLimit() {
        var request = new FileSizeLimitUpdateRequest(10737418237L);
        ApiResponse<Void> response = putForObject("file-size-limit", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Transactional
    @Test
    void putFileSizeLimit_null이면_제한없음() {
        var request = new FileSizeLimitUpdateRequest(null);
        ApiResponse<Void> response = putForObject("file-size-limit", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void putFileSizeLimit_음수이면_400() {
        var request = new FileSizeLimitUpdateRequest(-1L);
        putForObject("file-size-limit", request, new TypeReference<>() {}, status().isBadRequest());
    }

    @Test
    void putFileSizeLimit_0이면_400() {
        var request = new FileSizeLimitUpdateRequest(0L);
        putForObject("file-size-limit", request, new TypeReference<>() {}, status().isBadRequest());
    }

    @Test
    void getMfaStatus() {
        ApiResponse<MfaStatusResponse> response = getForObject("mfa-status", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response);
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    //@Transactional
    @Test
    void putMfaStatus() {
        var request = new MfaStatusUpdateRequest(true);
        ApiResponse<Void> response = putForObject("mfa-status", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void putMfaStatus_null이면_400() {
        var request = new MfaStatusUpdateRequest(null);
        putForObject("mfa-status", request, new TypeReference<>() {}, status().isBadRequest());
    }

    @Test
    void getAccessRestrictionStatus() {
        ApiResponse<AccessRestrictionResponse> response = getForObject("access-restriction-status", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response);
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertNotNull(response.getData());
        Assertions.assertNotNull(response.getData().allowedCountries());
        Assertions.assertNotNull(response.getData().allowedIps());
    }

    //@Transactional
    @Test
    void putAccessRestrictionStatus() {
        var request = new AccessRestrictionUpdateRequest(true);
        ApiResponse<Void> response = putForObject("access-restriction-status", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void putAccessRestrictionStatus_null이면_400() {
        var request = new AccessRestrictionUpdateRequest(null);
        putForObject("access-restriction-status", request, new TypeReference<>() {}, status().isBadRequest());
    }

    @Test
    void getShareExists() {
        ApiResponse<ShareExistsResponse> response = getForObject("shares/exists", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response);
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertNotNull(response.getData());
    }

}