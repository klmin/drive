package com.thinkfree.drive.api.v1.token.usecase;

import com.thinkfree.drive.api.v1.token.response.TokenResponse;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenReissueUseCase {

    private final DefaultRefreshTokenService refreshTokenService;

    public TokenResponse reissueTokens(Integer userSeq, String refreshToken, HttpServletRequest httpRequest) {
        var refreshTokenReissueResult = refreshTokenService.reissueTokens(userSeq, refreshToken);
        return TokenResponse.of(refreshTokenReissueResult.accessToken(), refreshTokenReissueResult.refreshToken());
    }
}
