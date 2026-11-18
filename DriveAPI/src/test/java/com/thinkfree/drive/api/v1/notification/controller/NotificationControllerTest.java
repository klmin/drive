package com.thinkfree.drive.api.v1.notification.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.notification.request.NotificationListRequest;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(roles = "ROLE_USER")
class NotificationControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/notifications");
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 914)
    @Test
    void findAllPaginated() {
        
        Boolean ir = null;
        Object cv = null;
        Long ci = null;
        ResourceType ct = null;
        Integer ps = 10;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);
        var request = new NotificationListRequest(ir);

        var params = multiValueMap(request, cursorPagingRequest);

        ApiResponse<List<StarredListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());
        
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 21)
    @Test
    void markAsAllRead(){
        
        String url = StringUtil.buildUrl("read-all");

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 21)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 21 의 알림 2828 없음(NOTIFICATION_002), 실행 시 dev 알림 읽음 상태를 바꿈")
    @Test
    void markAsReadAndGetUnreadCount(){
        
        Long notificationSeq = 2828L;
        String url = StringUtil.buildUrl(notificationSeq, "read");

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 21)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. user 21 의 알림 2828 없음(NOTIFICATION_002), 실행 시 dev 알림 읽음 상태를 바꿈")
    @Test
    void markAsUnreadAndGetUnreadCount(){
        
        Long notificationSeq = 2828L;
        String url = StringUtil.buildUrl(notificationSeq, "unread");

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }
}