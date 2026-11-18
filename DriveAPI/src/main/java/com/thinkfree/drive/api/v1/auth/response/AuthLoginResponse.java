package com.thinkfree.drive.api.v1.auth.response;

import com.thinkfree.drive.api.v1.auth.mfa.response.MfaChallengeResponse;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaSetupChallengeResponse;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.auth.result.LoginResult;
import org.springframework.http.HttpStatus;

public interface AuthLoginResponse {

    default HttpStatus httpStatus() {
        return HttpStatus.OK;
    }

    /** 사용자 로그인 결과 → 웹 응답(로그인 완료 200, MFA 챌린지·설정 챌린지 428 — 상태는 각 응답의 httpStatus) */
    static AuthLoginResponse from(AuthLoginResult result) {
        if (result instanceof LoginResult login) {
            return LoginResponse.from(login);
        }
        if (result instanceof AuthLoginResult.MfaChallenge mfa) {
            return MfaChallengeResponse.from(mfa);
        }
        if (result instanceof AuthLoginResult.MfaSetupChallenge mfaSetup) {
            return MfaSetupChallengeResponse.from(mfaSetup);
        }
        throw new IllegalArgumentException("Unsupported result type: " + result.getClass());
    }
}
