package com.thinkfree.drive.oauth2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * OAuth2 인가 플로우 전용 로그인 principal — JDBC 인가 상태 저장소 직렬화 대응.
 *
 * JdbcOAuth2AuthorizationService는 인가 건을 저장할 때 로그인 Authentication(principal 포함)을
 * Jackson으로 직렬화해 oauth2_authorization.attributes에 넣고, 토큰 교환/refresh 때 역직렬화한다.
 * 앱 전역의 CustomUserDetails는 권한을 람다로 반환해 직렬화가 불가능하고 Jackson 허용목록에도
 * 없으므로, OAuth2 로그인에는 이 클래스를 principal로 쓴다 (허용목록 등록은
 * OAuth2AuthorizationServerConfig가 JacksonMixin으로 수행).
 *
 * ⚠️ 클래스명·패키지를 바꾸면 attributes에 저장된 FQCN(@JsonTypeInfo Id.CLASS)과 어긋나
 * 기존 인가 행이 역직렬화 실패한다 — 개명 시 oauth2_authorization 정리 필요.
 *
 * 토큰 커스터마이저가 여기서 userSeq·동작 역할을 꺼내 subject·role 클레임을 만든다.
 *
 * Serializable은 spring-session-jdbc 대응 — 세션 SecurityContext의 principal로
 * SPRING_SESSION_ATTRIBUTES에 JDK 직렬화되어 저장된다 (JDBC 인가 저장소의 Jackson
 * 직렬화와 별개 경로, 둘 다 필요).
 */
@Getter
public class OAuth2UserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer userSeq;
    private final Integer tenantSeq;
    private final String userId;
    private final String userName;
    /**
     * 동작 역할 authority 1개(브리지가 정함, 지금은 {@code [ROLE_USER]}) — 토큰 role 클레임으로 나간다.
     * 인가 서버 체인은 로그인 여부만 보고, /mcp 인가는 McpBearerTokenAuthenticationFilter 가 요청마다 이 역할의 보유를 DB 로 다시 확인한다.
     * 목록 타입은 저장된 세션·인가 행과의 직렬화 호환 때문에 유지.
     * ArrayList 고정 — Jackson 허용목록(SecurityJackson2Modules)에 있는 타입이어야 attributes 역직렬화가 된다.
     * 다중 role 이전 행(roleType 단일)은 Jackson 은 ignoreUnknown, JDK 세션은 필드 추가 호환으로 읽혀 빈 목록이 된다({@link #readResolve}).
     */
    private final ArrayList<String> roles;

    public OAuth2UserPrincipal(Integer userSeq, Integer tenantSeq, String userId, String userName, List<String> roles) {
        this.userSeq = userSeq;
        this.tenantSeq = tenantSeq;
        this.userId = userId;
        this.userName = userName;
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    /** @param role OAuth 동작 역할(브리지가 정한 1개) */
    public static OAuth2UserPrincipal from(UserDetailData user, RoleType role) {
        return new OAuth2UserPrincipal(user.getUserSeq(), user.getTenantSeq(), user.getUserId(), user.getUserName(),
                List.of(role.getAuthority()));
    }

    /**
     * 동작 역할 — 토큰 role 클레임의 재료. 정확히 1개가 아니면(다중 role 이전·USER 고정 이전 행) null.
     * null 이면 커스터마이저가 role 클레임 없이 발급하고, /mcp 필터가 그 토큰을 USER 로 본다.
     */
    public RoleType actingRole() {
        if (roles.size() != 1) {
            return null;
        }
        return RoleType.fromAuthority(roles.get(0));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 람다 금지 — 직렬화 가능한 SimpleGrantedAuthority만 사용
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    /**
     * JDK 역직렬화(spring-session)는 생성자를 거치지 않아 roles 필드가 없던 행(다중 role 이전)은 null 로 읽힌다 —
     * 생성자로 다시 만들어 빈 목록으로 맞춘다(Jackson 경로는 {@code @JsonCreator} 라 이미 생성자를 거친다).
     */
    @Serial
    private Object readResolve() {
        return new OAuth2UserPrincipal(userSeq, tenantSeq, userId, userName, roles);
    }

    @Override
    public String getPassword() {
        return null;
    }

    /**
     * oauth2_authorization·oauth2_authorization_consent의 principal_name으로 저장되는 값.
     * 동의 스킵 판정과 (클라이언트, 사용자) 단위 조회의 키이므로 가변 가능한 userId가 아닌
     * 불변값 userSeq를 사용한다. 변경 시 기존 인가·동의 행과 매칭이 끊긴다 — 변경하면 초기화 필요.
     */
    @Override
    public String getUsername() {
        return String.valueOf(userSeq);
    }

    /**
     * Jackson 허용목록 등록 + 직렬화 규약(필드만 쓰고 읽기, 생성자 복원).
     * ObjectMapper.addMixIn(OAuth2UserPrincipal.class, JacksonMixin.class)로 활성화.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class JacksonMixin {
        @JsonCreator
        JacksonMixin(@JsonProperty("userSeq") Integer userSeq,
                     @JsonProperty("tenantSeq") Integer tenantSeq,
                     @JsonProperty("userId") String userId,
                     @JsonProperty("userName") String userName,
                     @JsonProperty("roles") List<String> roles) {
        }
    }
}
