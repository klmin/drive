package com.thinkfree.drive.api.v1.auth.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thinkfree.drive.auth.result.LoginResult;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class LoginResponse implements AuthLoginResponse {
    private Integer userSeq;
    private String userId;
    private String userName;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private List<RoleType> roles;
    private UserLanguage userLanguage;
    private DefaultLandingPage defaultLandingPage;
    /** Set-Cookie 로만 내려준다 */
    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private Boolean hasChangedPassword;
    private String baseDomain;
    private Boolean isFirstLogin;

    /** 로그인 결과 → 웹 응답(필드 1:1 — isFirstLogin 은 관리자 로그인에서만 값, 그 외 null) */
    public static LoginResponse from(LoginResult result) {
        return LoginResponse.builder()
                            .userSeq(result.getUserSeq())
                            .userId(result.getUserId())
                            .userName(result.getUserName())
                            .totalQuotaByte(result.getTotalQuotaByte())
                            .useQuotaByte(result.getUseQuotaByte())
                            .roles(result.getRoles())
                            .userLanguage(result.getUserLanguage())
                            .defaultLandingPage(result.getDefaultLandingPage())
                            .accessToken(result.getAccessToken())
                            .refreshToken(result.getRefreshToken())
                            .hasChangedPassword(result.getHasChangedPassword())
                            .baseDomain(result.getBaseDomain())
                            .isFirstLogin(result.getIsFirstLogin())
                            .build();
    }
}
