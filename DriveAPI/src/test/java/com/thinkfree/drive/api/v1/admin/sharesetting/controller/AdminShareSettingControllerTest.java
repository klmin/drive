package com.thinkfree.drive.api.v1.admin.sharesetting.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.sharesetting.request.AdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.admin.sharesetting.response.AdminShareSettingResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom
@Transactional
class AdminShareSettingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/share-settings");
    }

    @Test
    void find() {

        ApiResponse<AdminShareSettingResponse> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    /** 공유 on + 접근범위 일부 허용 — 정상. */
    @Test
    @WithMockUserCustom(userSeq = 876, tenantSeq = 5)
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void saveShareEnabled() {
        setTestHost("test5.drive.thinkfree.com");
        var request = new AdminShareSettingSaveRequest(true, true, true, true, true);

        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    /** 공유 off — 나머지 필드는 모두 생략(null) 가능. */
    @Test
    @WithMockUserCustom(userSeq = 876, tenantSeq = 5)
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void saveShareDisabledWithMinimalPayload() {
        setTestHost("test5.drive.thinkfree.com");
        var request = new AdminShareSettingSaveRequest(false, null, null, null, null);

        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
    }

    /** 공유 사용 여부(se)는 항상 필수. se=null 이면 거부. */
    @Test
    void saveFailWhenShareEnabledNull() {

        var request = new AdminShareSettingSaveRequest(null, true, true, true, true);

        assertBadRequest(request);
    }

    /** 공유 on 인데 접근범위 4개 중 하나라도 누락(esa=null)되면 거부. */
    @Test
    void saveFailWhenEnabledButPermissionMissing() {

        var request = new AdminShareSettingSaveRequest(true, null, true, true, false);

        assertBadRequest(request);
    }

    /** 공유 on 인데 접근범위(제한됨/조직내부/전체공개)가 모두 false 면 거부(외부공유 esa 는 scope 아님). */
    @Test
    void saveFailWhenEnabledButNoScopeAllowed() {

        var request = new AdminShareSettingSaveRequest(true, true, false, false, false);

        assertBadRequest(request);
    }

    /** 공유 on + 전체공개(pa=true)인데 외부공유(esa)가 꺼져 있으면 거부. */
    @Test
    void saveFailWhenPublicWithoutExternal() {

        var request = new AdminShareSettingSaveRequest(true, false, false, false, true);

        assertBadRequest(request);
    }

    private void assertBadRequest(AdminShareSettingSaveRequest request) {

        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>() {}, status().isBadRequest());

        Assertions.assertEquals(BAD_REQUEST.value(), response.getCode());
    }
}
