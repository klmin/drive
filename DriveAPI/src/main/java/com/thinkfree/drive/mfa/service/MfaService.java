package com.thinkfree.drive.mfa.service;

import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.mfa.exception.MfaErrorCode;
import com.thinkfree.drive.mfa.handler.MfaHandler;
import com.thinkfree.drive.mfa.properties.MfaProperty;
import com.thinkfree.drive.mfa.registry.MfaHandlerRegistry;
import com.thinkfree.drive.mfa.result.MfaChallengeResult;
import com.thinkfree.drive.mfa.result.MfaLoginSetupResult;
import com.thinkfree.drive.mfa.result.MfaSetupChallengeResult;
import com.thinkfree.drive.mfa.result.MfaSetupResult;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MfaService {

    private final MfaProperty mfaProperty;
    private final MfaHandlerRegistry mfaHandlerRegistry;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    
    public void confirmSetup(int userSeq, String code, MfaMethod method) {
        mfaHandlerRegistry.get(method).confirm(userSeq, code);
    }

    public void verify(UserAuthData user, String code) {

        DriveAssert.throwIfAnyNull(MfaErrorCode.MFA_NOT_ENABLED, user.getMfaMethod(), user.getMfaSecretKey());
        DriveAssert.throwIfFalse(user.getMfaStatus().isEnabled(), MfaErrorCode.MFA_NOT_ENABLED);

        MfaHandler handler = mfaHandlerRegistry.get(user.getMfaMethod());
        var verify = handler.verify(user.getMfaSecretKey(), code);
        DriveAssert.throwIfFalse(verify, MfaErrorCode.MFA_INVALID_CODE);
    }

    public void cancel(int userSeq, int tenantSeq) {
        userService.cancelMfa(userSeq, tenantSeq);
    }

    public int cleanupPendingSetup(Instant now) {
        Instant cutoffAt = now.minus(mfaProperty.totp().setup().clearTtl());
        return userService.clearPendingMfaTotp(cutoffAt, DriveConstants.SUPER_ADMIN_USER_SEQ);
    }
    
    public MfaChallengeResult issueMfaChallenge(int userSeq, MfaMethod mfaMethod){
        Duration ttl = mfaProperty.totp().login().challengeTokenTtl();
        String token = jwtProvider.createMfaChallengeToken(userSeq, ttl);
        return new MfaChallengeResult(token, (int) ttl.toSeconds(), mfaMethod.name().toLowerCase());
    }

    public MfaSetupChallengeResult issueMfaSetupChallenge(int userSeq) {
        Duration ttl = mfaProperty.totp().login().setupChallengeTokenTtl();
        String setupToken = jwtProvider.createMfaSetupChallengeToken(userSeq, ttl);
        return new MfaSetupChallengeResult(setupToken, (int) ttl.toSeconds());
    }

    public MfaLoginSetupResult setupMfaForLogin(int userSeq, String userId, MfaMethod method) {
        Duration ttl = mfaProperty.totp().login().confirmChallengeTokenTtl();
        MfaSetupResult result = mfaHandlerRegistry.get(method).setup(userSeq, userId);
        String confirmToken = jwtProvider.createMfaConfirmChallengeToken(userSeq, ttl);
        return new MfaLoginSetupResult(result.otpAuthUri(), confirmToken, (int) ttl.toSeconds());
    }

}
