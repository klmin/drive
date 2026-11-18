package com.thinkfree.drive.resource.share.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.share.service.ResourceShareTokenService;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.util.Base62TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultResourceShareTokenService implements ResourceShareTokenService {

    private static final int TOKEN_LENGTH = 16;
    private static final int MAX_ATTEMPTS = 10;
    private final ResourceFindService resourceFindService;

    @Retryable(
        retryFor = { ApiRuntimeException.class },
        maxAttempts = MAX_ATTEMPTS,
        backoff = @Backoff(delay = 100)
    )
    @Override
    public String createNewToken() {

        String token = Base62TokenGenerator.generate(TOKEN_LENGTH);
        
        if (resourceFindService.existsByShareToken(token)) {
            log.error("[Link is Duplicate] retryCount: {}, token: {}", RetryUtil.getRetryCount(), token);
            throw ApiRuntimeException.type(ResourceErrorCode.CREATE_LINK_FAILED);
        }

        return token;
    }

    @Recover
    protected String createNewTokenRecover(ApiRuntimeException e) {
        log.error("[Link is Duplicate retry failed] retryCount: {}", RetryUtil.getRetryCount());
        throw e;
    }
}
