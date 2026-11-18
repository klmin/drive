package com.thinkfree.drive.oauth2.connection.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 연동 앱 관리 API 테스트 — dev DB의 실 데이터 기준 (TB_OAUTH2_CLIENT_CONNECTION에 데이터 선행 입력 필요).
 */
@Slf4j
@WithMockUserCustom(roles = "ROLE_USER", userSeq = 859)
class OAuth2ConnectionControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/oauth2/connections");
    }

    @Test
    void findConnections() {
        
        ApiResponse<List<Map<String, Object>>> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertNotNull(response.getData());

        response.getData().forEach(connection -> log.info("connection : {}", connection));
    }

    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. 연결 seq 11 없음(OAUTH2_001), 실행 시 dev OAuth2 연결 삭제(파괴적)")
    @Test
    void disconnect() {

        Long connectionSeq = 11L;

        ApiResponse<Void> response = deleteForObject(String.valueOf(connectionSeq), null,
                new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void disconnectNotFound() {

        Long connectionSeq = 999999999L;

        deleteForObject(String.valueOf(connectionSeq), null, new TypeReference<ApiResponse<Void>>() {}, status().isNotFound());
    }

    @Test
    void disconnectOtherUsersConnection() {

        // 남의 connectionSeq — 존재 여부를 노출하지 않도록 없는 것과 동일하게 404
        Long connectionSeq = 2L;

        deleteForObject(String.valueOf(connectionSeq), null, new TypeReference<ApiResponse<Void>>() {}, status().isNotFound());
    }
}
