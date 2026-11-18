package com.thinkfree.drive.auth.token.refresh.service.impl;

import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.model.LoginTokenClaims;
import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import com.thinkfree.drive.auth.token.refresh.repository.RefreshTokenRepository;
import com.thinkfree.drive.auth.token.refresh.result.RefreshTokenReissueResult;
import com.thinkfree.drive.auth.token.refresh.service.RefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DefaultRefreshTokenService implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final UserRoleFindService userRoleFindService;

    @Override
    public void saveRefreshToken(int userSeq, String sessionId, String refreshToken) {
        log.info("{ RefreshTokenService } : 리프레시 토큰 저장 - userSeq={}, sessionId={}", userSeq, sessionId);
        RefreshToken token = RefreshToken.createRefreshToken(userSeq, sessionId, refreshToken);
        refreshTokenRepository.save(token);
    }

    @Override
    public void deleteRefreshTokenByUserSeq(int userSeq) {
        refreshTokenRepository.deleteByUserSeq(userSeq);
    }

    @Override
    public void deleteRefreshTokenBySessionId(String sessionId) {
        refreshTokenRepository.deleteBySessionId(sessionId);
    }

    @Override
    public void deleteRefreshTokenByUserSeqList(List<Integer> userSeqList) {
        var result = refreshTokenRepository.deleteByUserSeqList(userSeqList);
        log.info("[refresh token 삭제 : {}건]", result);
    }

    @Override
    public RefreshTokenReissueResult reissueTokens(Integer userSeq, String refreshToken) {
        log.info("{ RefreshTokenService } : 토큰 재발급 진입");

        String sessionId;
        RoleType role;
        try {
            LoginTokenClaims claims = jwtProvider.parseLoginTokenClaims(refreshToken);
            sessionId = claims.sessionId();
            role = claims.role();
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiRuntimeException.type(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        // 같은 역할로만 재발급 — 역할 클레임 없는 토큰(다중 role 이전)·회수된 역할은 재로그인
        if (role == null || !userRoleFindService.holds(userSeq, role)) {
            throw ApiRuntimeException.type(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        // 고칠 행을 한 번 로드해 판정(토큰 값 일치 + 만료 전) → RTR 은 같은 관리 엔티티를 고쳐 커밋 때 변경 감지
        RefreshToken token = refreshTokenRepository.findBySessionId(sessionId);
        if (token == null || !token.isValid(refreshToken, Instant.now())) {
            log.info("{ RefreshTokenService } : 유효하지 않은 리프레시 토큰");
            throw ApiRuntimeException.type(AuthErrorCode.AUTH_INVALID_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(userSeq, sessionId, role);
        String newRefreshToken = jwtProvider.createRefreshToken(userSeq, sessionId, role);

        token.updateRefreshToken(newRefreshToken);

        return new RefreshTokenReissueResult(newAccessToken, newRefreshToken);
    }

    @Override
    public int deleteExpiredTokens(Instant expirationThreshold) {
        return refreshTokenRepository.deleteExpiredTokens(expirationThreshold);
    }
}
