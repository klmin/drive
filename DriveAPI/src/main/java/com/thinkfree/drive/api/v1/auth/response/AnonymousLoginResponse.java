package com.thinkfree.drive.api.v1.auth.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thinkfree.drive.auth.result.AnonymousLoginResult;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class AnonymousLoginResponse {
    private Integer userSeq;
    private String userId;
    private String userName;
    private List<RoleType> roles;
    private UserLanguage userLanguage;
    /** Set-Cookie 로만 내려준다 */
    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private String baseDomain;

    public static AnonymousLoginResponse from(AnonymousLoginResult result) {
        return AnonymousLoginResponse.builder()
                .userSeq(result.userSeq())
                .userId(result.userId())
                .userName(result.userName())
                .roles(result.roles())
                .userLanguage(result.userLanguage())
                .accessToken(result.accessToken())
                .refreshToken(result.refreshToken())
                .baseDomain(result.baseDomain())
                .build();
    }
}
