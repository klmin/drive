package com.thinkfree.drive.api.v1.userlog.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.userlog.response.UserLogListResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom
@Transactional
class UserLogControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/users");
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void getCurrentUserLogs() {

        Object cv = null;
        Long ci = null;
        ResourceType ct = null;
        Integer ps = 10;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);
        MultiValueMap<String, String> params = multiValueMap(cursorPagingRequest);

        String url = StringUtil.buildUrl("log");
        ApiResponse<List<UserLogListResponse>> response = getForObjectWithMultiParams(url, params, new TypeReference<>() {}, status().isOk());

        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
}
