package com.thinkfree.drive.auth.result;

import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;

import java.util.List;

/** 공개 링크 익명 로그인 결과(익명 사용자 요약 + 토큰) — 웹 응답 변환은 어댑터(AuthUseCase) */
public record AnonymousLoginResult
(
    Integer userSeq,
    String userId,
    String userName,
    List<RoleType> roles,
    UserLanguage userLanguage,
    String accessToken,
    String refreshToken,
    String baseDomain
)
{
    public static AnonymousLoginResult of(UserDetailData user, String accessToken, String refreshToken, String baseDomain) {
        return new AnonymousLoginResult(
                user.getUserSeq(),
                user.getUserId(),
                user.getUserName(),
                List.of(RoleType.ANONYMOUS), // 익명 계정은 단독 역할
                user.getUserLanguage(),
                accessToken,
                refreshToken,
                baseDomain
        );
    }
}
