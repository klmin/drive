package com.thinkfree.drive.signup.result;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;

import java.util.List;

public record SignupResult
(
    Integer userSeq,
    String userId,
    String userName,
    Long totalQuotaByte,
    Long useQuotaByte,
    List<RoleType> roles,
    UserLanguage userLanguage,
    DefaultLandingPage defaultLandingPage,
    String accessToken,
    String refreshToken
)
{
}
