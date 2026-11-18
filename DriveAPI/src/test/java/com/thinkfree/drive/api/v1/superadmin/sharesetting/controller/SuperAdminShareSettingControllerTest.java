package com.thinkfree.drive.api.v1.superadmin.sharesetting.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.ShareExistsResponse;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.request.SuperAdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.response.SuperAdminShareSettingResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom(roles = "ROLE_SUPER_ADMIN")
class SuperAdminShareSettingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/super-admin/share-settings");
    }

    @Test
    void find() {
        
        ApiResponse<SuperAdminShareSettingResponse> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());
      
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
       
    }

    @Test
    void getShareExists() {

        ApiResponse<ShareExistsResponse> response = getForObject("shares/exists", null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void saveEnforceFalse() {
        
        var request = new SuperAdminShareSettingSaveRequest(false, true, true, true, true, true);

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void saveEnforceTrue() {
        
        var request = new SuperAdminShareSettingSaveRequest(true, true, false, true, false, false);

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void saveShareDisabled() {

        // 전체적용 + 공유 기능 off
        var request = new SuperAdminShareSettingSaveRequest(true, false, false, true, true, false);

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    /** 적용범위(e) 는 항상 필수. e=null 이면 거부. */
    @Test
    void saveFailWhenEnforceNull() {

        var request = new SuperAdminShareSettingSaveRequest(null, true, true, true, true, true);

        assertBadRequest(request);
    }

    /** 전체적용(e=true)인데 공유 사용 여부(se)가 누락되면 거부. */
    @Test
    void saveFailWhenEnforcedButShareEnabledNull() {

        var request = new SuperAdminShareSettingSaveRequest(true, null, true, true, true, true);

        assertBadRequest(request);
    }

    /** 전체적용 + 공유 on 인데 접근범위 4개 중 하나라도 누락(esa=null)되면 거부. */
    @Test
    void saveFailWhenEnabledButPermissionMissing() {

        var request = new SuperAdminShareSettingSaveRequest(true, true, null, true, true, false);

        assertBadRequest(request);
    }

    /** 전체적용 + 공유 on 인데 접근범위(제한됨/조직내부/전체공개)가 모두 false 면 거부(외부공유 esa 는 scope 아님). */
    @Test
    void saveFailWhenEnabledButNoScopeAllowed() {

        var request = new SuperAdminShareSettingSaveRequest(true, true, true, false, false, false);

        assertBadRequest(request);
    }

    /** 전체적용 + 공유 on + 전체공개(pa=true)인데 외부공유(esa)가 꺼져 있으면 거부. */
    @Test
    void saveFailWhenPublicWithoutExternal() {

        var request = new SuperAdminShareSettingSaveRequest(true, true, false, false, false, true);

        assertBadRequest(request);
    }

    /** 개별적용(e=false)이면 나머지 필드는 모두 생략(null) 가능 — 검증 통과. */
    @Test
    void saveEnforceFalseWithMinimalPayload() {

        var request = new SuperAdminShareSettingSaveRequest(false, null, null, null, null, null);

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
    }

    private void assertBadRequest(SuperAdminShareSettingSaveRequest request) {

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isBadRequest());

        Assertions.assertEquals(BAD_REQUEST.value(), response.getCode());
    }
}
