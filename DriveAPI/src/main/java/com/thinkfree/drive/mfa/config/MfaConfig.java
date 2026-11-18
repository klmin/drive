package com.thinkfree.drive.mfa.config;

import com.thinkfree.drive.mfa.properties.MfaProperty;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MfaConfig {
    
    private static final int SECRET_LENGTH = 32;
    
    private final MfaProperty mfaProperty;

    @Bean
    public CodeVerifier codeVerifier(){
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, mfaProperty.totp().digits());
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(mfaProperty.totp().allowedSkewSteps());
        return verifier;
    }
    
    @Bean
    public SecretGenerator secretGenerator() {
        return new DefaultSecretGenerator(SECRET_LENGTH);
    }
    
}
