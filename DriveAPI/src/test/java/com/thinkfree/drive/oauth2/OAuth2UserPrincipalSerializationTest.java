package com.thinkfree.drive.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * OAuth2UserPrincipal 의 roles(동작 역할 1개) — 인가 저장소(Jackson, OAuth2AuthorizationServerConfig 와 같은 매퍼 구성)와
 * spring-session(JDK 직렬화) 왕복, 다중 role 이전 형식(roleType 단일) 행도 깨지지 않고 읽히는지.
 */
@DisplayName("OAuth2UserPrincipal — roles 직렬화 왕복·구 형식 호환")
class OAuth2UserPrincipalSerializationTest {

    private static ObjectMapper authorizationMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(JdbcOAuth2AuthorizationService.class.getClassLoader()));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(OAuth2UserPrincipal.class, OAuth2UserPrincipal.JacksonMixin.class);
        return objectMapper;
    }

    private static OAuth2UserPrincipal principal() {
        UserDetailData user = mock(UserDetailData.class);
        given(user.getUserSeq()).willReturn(10);
        given(user.getTenantSeq()).willReturn(1);
        given(user.getUserId()).willReturn("u@test.com");
        given(user.getUserName()).willReturn("u");
        return OAuth2UserPrincipal.from(user, RoleType.USER);
    }

    @Test
    @DisplayName("from — 브리지가 정한 동작 역할 1개, actingRole 로 다시 꺼냄")
    void fromActingRole() {
        assertThat(principal().getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        assertThat(principal().actingRole()).isEqualTo(RoleType.USER);
    }

    @Test
    @DisplayName("actingRole — USER 고정 이전 행(역할 여러 개)·다중 role 이전 행(비어 있음)은 null")
    void actingRoleOfLegacyRows() {
        assertThat(new OAuth2UserPrincipal(10, 1, "u@test.com", "u", List.of("ROLE_ADMIN", "ROLE_USER")).actingRole()).isNull();
        assertThat(new OAuth2UserPrincipal(10, 1, "u@test.com", "u", null).actingRole()).isNull();
    }

    @Test
    @DisplayName("Jackson — 로그인 Authentication 저장·복원 시 roles 유지")
    void jacksonRoundTrip() throws Exception {
        ObjectMapper mapper = authorizationMapper();
        OAuth2UserPrincipal principal = principal();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        String json = mapper.writeValueAsString(authentication);
        UsernamePasswordAuthenticationToken restored = mapper.readValue(json, UsernamePasswordAuthenticationToken.class);

        OAuth2UserPrincipal restoredPrincipal = (OAuth2UserPrincipal) restored.getPrincipal();
        assertThat(restoredPrincipal.getUserSeq()).isEqualTo(10);
        assertThat(restoredPrincipal.getRoles()).containsExactly("ROLE_USER");
        assertThat(restoredPrincipal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Jackson — 다중 role 이전 형식(roleType 단일) principal 도 역직렬화(roles 비어 있음)")
    void jacksonLegacyFormat() throws Exception {
        String legacy = "{\"@class\":\"com.thinkfree.drive.oauth2.OAuth2UserPrincipal\",\"userSeq\":10,\"tenantSeq\":1,"
                + "\"userId\":\"u@test.com\",\"userName\":\"u\",\"roleType\":\"ROLE_USER\"}";

        OAuth2UserPrincipal restored = authorizationMapper().readValue(legacy, OAuth2UserPrincipal.class);

        assertThat(restored.getUserSeq()).isEqualTo(10);
        assertThat(restored.getRoles()).isEmpty();
        assertThat(restored.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("JDK 직렬화(spring-session) — roles 유지")
    void jdkRoundTrip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(principal());
        }
        OAuth2UserPrincipal restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (OAuth2UserPrincipal) in.readObject();
        }

        assertThat(restored.getRoles()).isEqualTo(List.of("ROLE_USER"));
        assertThat(restored.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("JDK 직렬화 — roles 가 null 로 읽히는 행(다중 role 이전 세션)은 readResolve 로 빈 목록, actingRole null")
    void jdkNullRolesResolvedToEmpty() throws Exception {
        OAuth2UserPrincipal principal = principal();
        Field roles = OAuth2UserPrincipal.class.getDeclaredField("roles");
        roles.setAccessible(true);
        roles.set(principal, null); // 필드가 없던 옛 세션 바이트를 역직렬화한 상태와 같음

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(principal);
        }
        OAuth2UserPrincipal restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (OAuth2UserPrincipal) in.readObject();
        }

        assertThat(restored.getRoles()).isEmpty();
        assertThat(restored.getAuthorities()).isEmpty();
        assertThat(restored.actingRole()).isNull();
        assertThat(restored.getUserSeq()).isEqualTo(10);
    }
}
