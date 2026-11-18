package com.thinkfree.drive.auth.token.service;

import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenService;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.superadmin.token.repository.TenantAccessTokenRepository;
import com.thinkfree.drive.user.token.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TokenService {

    private final DefaultRefreshTokenService refreshTokenService;
    private final BlacklistTokenService blacklistTokenService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TenantAccessTokenRepository tenantAccessTokenRepository;

    public void deleteExpiredTokens(Instant expirationThreshold){
        var blacklistDeleteCnt = blacklistTokenService.deleteExpiredTokens(expirationThreshold);
        if(blacklistDeleteCnt > 0) {
            log.info("[blacklist token delete : {} 건]", blacklistDeleteCnt);
        }
        var refreshTokenDeleteCnt = refreshTokenService.deleteExpiredTokens(expirationThreshold);
        if(refreshTokenDeleteCnt > 0) {
            log.info("[refresh token delete : {} 건]", refreshTokenDeleteCnt);
        }
        var passwordResetTokenDeleteCnt = passwordResetTokenRepository.deleteExpiredTokens(expirationThreshold);
        if(passwordResetTokenDeleteCnt > 0) {
            log.info("[password reset token delete : {} 건]", passwordResetTokenDeleteCnt);
        }
        var tenantAccessTokenDeleteCnt = tenantAccessTokenRepository.deleteExpiredTokens(expirationThreshold);
        if(tenantAccessTokenDeleteCnt > 0) {
            log.info("[tenant access token delete : {} 건]", tenantAccessTokenDeleteCnt);
        }
    }
    
    public void deleteByUsers(List<Integer> userSeqList){
        refreshTokenService.deleteRefreshTokenByUserSeqList(userSeqList);
        blacklistTokenService.deleteByUsers(userSeqList);
    }
}
