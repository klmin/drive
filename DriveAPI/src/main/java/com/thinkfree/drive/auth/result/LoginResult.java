package com.thinkfree.drive.auth.result;

import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 로그인 완료 결과(토큰 발급 + 사용자 요약). 웹 응답 {@code LoginResponse} 와 같은 필드·같은 getter 이름
 * (로그가 {@code getIsFirstLogin()} 을 읽는다) — isFirstLogin 은 관리자 로그인에서만 채우고 나머지는 null.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginResult implements AuthLoginResult {
    private final Integer userSeq;
    private final String userId;
    private final String userName;
    private final Long totalQuotaByte;
    private final Long useQuotaByte;
    private final List<RoleType> roles;
    private final UserLanguage userLanguage;
    private final DefaultLandingPage defaultLandingPage;
    private final String accessToken;
    private final String refreshToken;
    private final Boolean hasChangedPassword;
    private final String baseDomain;
    private final Boolean isFirstLogin;

    public static LoginResult of(UserAuthData user, List<RoleType> roles, String accessToken, String refreshToken, Boolean hasChangedPassword, String baseDomain) {
        return of(user, roles, accessToken, refreshToken, hasChangedPassword, baseDomain, null);
    }

    /** roles = 계정의 보유 역할 전부(tb_user_role). 로그인 역할(토큰 role 클레임)은 응답에 싣지 않는다 — 어느 로그인 API 로 들어왔는지가 곧 그 역할 */
    public static LoginResult of(UserAuthData user, List<RoleType> roles, String accessToken, String refreshToken, Boolean hasChangedPassword, String baseDomain, Boolean isFirstLogin) {
        return LoginResult.builder()
                          .userSeq(user.getUserSeq())
                          .userId(user.getUserId())
                          .userName(user.getUserName())
                          .totalQuotaByte(user.getTotalQuotaByte())
                          .useQuotaByte(user.getUseQuotaByte())
                          .roles(roles)
                          .userLanguage(user.getUserLanguage())
                          .defaultLandingPage(user.getDefaultLandingPage())
                          .accessToken(accessToken)
                          .refreshToken(refreshToken)
                          .hasChangedPassword(hasChangedPassword)
                          .baseDomain(baseDomain)
                          .isFirstLogin(isFirstLogin)
                          .build();
    }
}
