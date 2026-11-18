package com.thinkfree.drive.mfa.handler;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.mfa.exception.MfaErrorCode;
import com.thinkfree.drive.mfa.properties.MfaProperty;
import com.thinkfree.drive.mfa.result.MfaSetupResult;
import com.thinkfree.drive.mfa.totp.OtpAuthUri;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.valid.DriveAssert;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TotpMfaHandler implements MfaHandler {

    private final MfaProperty mfaProperty;
    private final CodeVerifier codeVerifier;
    private final SecretGenerator secretGenerator;
    private final UserFindService userFindService;
    private final UserService userService;

    @Override
    public MfaMethod supports() {
        return MfaMethod.TOTP;
    }

    @Override
    public MfaSetupResult setup(Integer userSeq, String userId) {

        UserAuthData user = userFindService.findAuthByUserSeq(userSeq);

        if (user.getMfaStatus().isEnabled()) {
            throw ApiRuntimeException.type(MfaErrorCode.MFA_ALREADY_ENABLED);
        }

        String secretKey = secretGenerator.generate();

        userService.setupMfaTotp(userSeq, secretKey);

        var totp = mfaProperty.totp();
        var otpAuthUri = OtpAuthUri.totp(totp.issuer(), userId, secretKey, HashingAlgorithm.SHA1, totp.digits(), totp.period().toSeconds());

        return new MfaSetupResult(otpAuthUri.toUriString());
    }

    @Override
    public void confirm(Integer userSeq, String code) {

        UserAuthData user = userFindService.findAuthByUserSeq(userSeq);

        if (!user.getMfaStatus().isPending()) {
            throw ApiRuntimeException.type(MfaErrorCode.MFA_NOT_PENDING);
        }

        var verify = verify(user.getMfaSecretKey(), code);
        DriveAssert.throwIfFalse(verify, MfaErrorCode.MFA_INVALID_CODE);

        var mfaConfirmDate = Instant.now();
        var cutoffAt = Instant.now().minus(mfaProperty.totp().setup().pendingTtl());

        userService.confirmMfaTotp(userSeq, mfaConfirmDate, cutoffAt);
    }

    @Override
    public boolean verify(String secretKey, String code) {
        DriveAssert.throwIfNoText(secretKey, MfaErrorCode.MFA_SECRET_KEY_NOT_FOUND);
        return codeVerifier.isValidCode(secretKey, code);
    }
}
