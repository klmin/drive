package com.thinkfree.drive.superadmin.token.service.impl;

import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.superadmin.exception.SuperAdminErrorCode;
import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import com.thinkfree.drive.superadmin.token.repository.TenantAccessTokenRepository;
import com.thinkfree.drive.superadmin.token.result.TenantAccessTokenConsumeResult;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.util.Base62TokenGenerator;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DefaultTenantAccessTokenService implements TenantAccessTokenService {

    @Value("${auth.token.tenant-access-expiry}")
    private Duration tenantAccessExpiry;

    private final TenantAccessTokenRepository tenantAccessTokenRepository;

    @Transactional
    @Override
    public String issue(Integer superAdminUserSeq, Integer tenantSeq) {
        tenantAccessTokenRepository.deleteByTenantSeq(tenantSeq);

        Instant now = Instant.now();
        String token = Base62TokenGenerator.generate(32);
        String tokenHash = TokenHasher.hash(token);
        tenantAccessTokenRepository.save(
                TenantAccessToken.of(superAdminUserSeq, tenantSeq, tokenHash, now, now.plus(tenantAccessExpiry))
        );

        return token;
    }

    @Transactional
    @Override
    public TenantAccessTokenConsumeResult consumeAndGet(String token) {
        String tokenHash = TokenHasher.hash(token);
        TenantAccessToken accessToken = tenantAccessTokenRepository.findByTokenHash(tokenHash);
        DriveAssert.throwIfNull(accessToken, SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN);
        DriveAssert.throwIfTrue(Instant.now().isAfter(accessToken.getExpirationDate()), SuperAdminErrorCode.EXPIRED_TENANT_ACCESS_TOKEN);
        // 1회용 보장 — 동시 소비에서 뒤 요청의 DELETE 는 앞 요청 커밋 뒤 0행(조회는 커밋 전이라 통과했어도 여기서 거부)
        DriveAssert.throwIfZero(tenantAccessTokenRepository.deleteByTokenHash(tokenHash), SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN);
        return new TenantAccessTokenConsumeResult(accessToken.getSuperAdminUserSeq(), accessToken.getTenantSeq());
    }
    @Override
    public void deleteByTenant(Integer tenantSeq) {
        tenantAccessTokenRepository.deleteByTenantSeq(tenantSeq);
    }
}
