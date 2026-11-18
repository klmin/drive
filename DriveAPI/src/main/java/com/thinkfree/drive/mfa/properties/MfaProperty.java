package com.thinkfree.drive.mfa.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("mfa")
public record MfaProperty
(
    Totp totp
)
{
    public record Totp(
        String issuer,
        Duration period,
        Integer allowedSkewSteps,
        Integer digits,
        Setup setup,
        Login login
    )
    {
        public record Setup(
            Duration pendingTtl,
            Duration clearTtl
        )
        {
        }

        public record Login(
            Duration challengeTokenTtl,
            Duration setupChallengeTokenTtl,
            Duration confirmChallengeTokenTtl
        )
        {
        }
    }
    
    
}
