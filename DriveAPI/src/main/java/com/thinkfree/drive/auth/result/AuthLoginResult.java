package com.thinkfree.drive.auth.result;

import com.thinkfree.drive.mfa.enums.MfaChallengeType;

import java.time.Instant;

/**
 * 사용자 로그인({@code AuthService.login}) 결과 — 로그인 완료({@link LoginResult}) 또는 MFA 단계 진입(챌린지·설정 챌린지).
 * 웹 응답 변환(HTTP 상태 포함)은 어댑터(AuthUseCase)가 한다. 만료 시각은 발급 시점(서비스)에 계산한다.
 */
public sealed interface AuthLoginResult
    permits LoginResult,
            AuthLoginResult.MfaChallenge,
            AuthLoginResult.MfaSetupChallenge {

    /** MFA 활성 사용자 — 챌린지 토큰 발급 */
    record MfaChallenge
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt,
        String challengeType
    )
        implements AuthLoginResult
    {
        public static MfaChallenge of(String token, Integer expiresInSeconds, String challengeType) {
            return new MfaChallenge(
                    MfaChallengeType.VERIFY,
                    token,
                    expiresInSeconds,
                    Instant.now().plusSeconds(expiresInSeconds),
                    challengeType
            );
        }
    }

    /** MFA 미설정 사용자 — 설정 챌린지 토큰 발급 */
    record MfaSetupChallenge
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt
    )
        implements AuthLoginResult
    {
        public static MfaSetupChallenge of(String setupToken, Integer expiresInSeconds) {
            return new MfaSetupChallenge(
                    MfaChallengeType.SETUP,
                    setupToken,
                    expiresInSeconds,
                    Instant.now().plusSeconds(expiresInSeconds)
            );
        }
    }
}
