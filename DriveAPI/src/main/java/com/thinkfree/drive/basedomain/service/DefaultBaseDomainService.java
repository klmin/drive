package com.thinkfree.drive.basedomain.service;

import com.thinkfree.drive.basedomain.address.BaseDomainAddress;
import com.thinkfree.drive.basedomain.converter.BaseDomainConverter;
import com.thinkfree.drive.basedomain.result.BaseDomainUpsertResult;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.basedomain.repository.BaseDomainRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultBaseDomainService implements BaseDomainService {

    /** 단일 베이스 운영 단계의 고정 PK. 멀티 확장 시 제거. */
    public static final int SINGLE_BASE_DOMAIN_SEQ = 1;

    private final BaseDomainConverter baseDomainConverter;
    private final BaseDomainRepository baseDomainRepository;
    /** 호스트 허용 형식은 주소 모드가 정한다 — SUBDOMAIN 은 도메인·localhost, PATH 는 IPv4 도(베이스 도메인 = 공개 호스트). 스킴·포트는 모드 무관. */
    private final TenantAddressingStrategy addressingStrategy;

    /**
     * @param baseDomain 입력값 {@code [scheme://]host[:port]} — 모양은 {@link BaseDomainAddress#parse}, 호스트 종류는 주소 모드가 검사.
     *                   저장은 호스트·스킴·포트로 나눠서.
     */
    @Override
    public BaseDomainUpsertResult upsert(String baseDomain) {
        BaseDomainAddress address = BaseDomainAddress.parse(baseDomain);
        if (!addressingStrategy.isAcceptableBaseDomain(address.host())) {
            throw ApiRuntimeException.type(BaseDomainErrorCode.INVALID_BASE_DOMAIN);
        }
        var entity = baseDomainConverter.toUpsertBaseDomain(address.host(), address.scheme(), address.port(), SINGLE_BASE_DOMAIN_SEQ);
        baseDomainRepository.upsert(entity);
        return new BaseDomainUpsertResult(entity.getBaseDomainSeq(), entity.getBaseDomain());
    }

}
