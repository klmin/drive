package com.thinkfree.drive.api.v1.admin.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserSearchResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom
class AdminUserSearchControllerTest extends AbstractMvcTest {

    /** 로그인 관리자(userSeq=1)와 같은 테넌트의 USER 계정 userId 일부 */
    private static final String USER_ID_KEYWORD = "1";
    /** 같은 계정의 userName 일부 */
    private static final String USER_NAME_KEYWORD = "윤선경";
    /** 어느 계정에도 걸리지 않는 검색어 */
    private static final String NO_MATCH_KEYWORD = "zzzz-no-match-zzzz";

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/users/search");
    }

    /**
     * rt·st는 List라 값 하나에 파라미터를 여러 번 실어야 한다.
     * AbstractMvcTest.multiValueMap(record)는 필드를 Map<String,String>으로 눌러서
     * 목록이 "[ADMIN, USER]" 한 덩어리가 되므로 여기선 쓰지 않는다.
     */
    private MultiValueMap<String, String> searchParams(String keyword, List<RoleType> roleTypes, List<UserStatus> userStatuses) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("sk", keyword);
        if (roleTypes != null) {
            roleTypes.forEach(roleType -> params.add("rt", roleType.name()));
        }
        if (userStatuses != null) {
            userStatuses.forEach(userStatus -> params.add("st", userStatus.name()));
        }
        return params;
    }

    @Test
    void search_byUserId() {
        var params = searchParams(USER_ID_KEYWORD, null, null);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertFalse(response.getData().isEmpty());
    }

    @Test
    void search_byUserName() {
        var params = searchParams(USER_NAME_KEYWORD, null, null);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty());
    }

    @Test
    void search_whenNoMatch_returnsEmptyList() {
        var params = searchParams(NO_MATCH_KEYWORD, null, null);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
    }

    /** rt·st 미지정 시 기본값 — API Key 발급 화면이 기대하는 범위다 */
    @Test
    void search_whenRoleAndStatusOmitted_returnsOnlyActiveUserRole() {
        var params = searchParams(USER_ID_KEYWORD, null, null);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertFalse(response.getData().isEmpty());
        response.getData().forEach(user -> {
            assertTrue(user.roles().contains(RoleType.USER));
            assertEquals(UserStatus.ACTIVE, user.status());
        });
    }

    @Test
    void search_whenRoleTypeGiven_returnsOnlyThatRole() {
        var params = searchParams(USER_ID_KEYWORD, List.of(RoleType.ADMIN), null);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        response.getData().forEach(user -> assertTrue(user.roles().contains(RoleType.ADMIN)));
    }

    /** 감사 로그 행위자 검색 — 탈퇴·삭제대기 계정까지 범위에 든다 */
    @Test
    void search_whenStatusesGiven_returnsOnlyThoseStatuses() {
        var statuses = List.of(UserStatus.PENDING_DELETE, UserStatus.DELETED);
        var params = searchParams(USER_ID_KEYWORD, null, statuses);

        ApiResponse<List<AdminUserSearchResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        response.getData().forEach(user -> assertTrue(statuses.contains(user.status())));
    }

    /** SUPER_ADMIN은 테넌트 관리자가 다룰 대상이 아니다 */
    @Test
    void search_whenRoleTypeIsNotSearchable_returnsBadRequest() {
        var params = searchParams(USER_ID_KEYWORD, List.of(RoleType.SUPER_ADMIN), null);

        ApiResponse<Void> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    void search_whenKeywordIsBlank_returnsBadRequest() {
        var params = searchParams("  ", null, null);

        ApiResponse<Void> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }
}
