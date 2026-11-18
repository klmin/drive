package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;
import com.thinkfree.drive.apikey.repository.ApiKeyQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultApiKeyFindService implements ApiKeyFindService {

    private final ApiKeyQueryRepository apiKeyQueryRepository;

    @Override
    public List<ApiKeyData> findAll(ApiKeyListQuery query) {
        return apiKeyQueryRepository.findAll(query);
    }

    @Override
    public List<ApiKeyData> findAllByRegisterUser(ApiKeyListQuery query) {
        return apiKeyQueryRepository.findAllByRegisterUser(query);
    }

    @Override
    public List<ApiKeyData> findAllForSuperAdmin(ApiKeyListQuery query) {
        return apiKeyQueryRepository.findAllForSuperAdmin(query);
    }

    @Override
    public ApiKeyData findActiveByApiKey(String apiKey, Integer tenantSeq) {
        return apiKeyQueryRepository.findActiveByApiKey(apiKey, tenantSeq);
    }

    @Override
    public Integer findTenantSeqByApiKeySeq(int apiKeySeq) {
        ApiKeyData data = apiKeyQueryRepository.findById(apiKeySeq); // PK 단건 DTO + Java 판정(W5)
        Integer tenantSeq = data == null ? null : data.getTenantSeq();
        DriveAssert.throwIfNull(tenantSeq, ApiKeyErrorCode.API_KEY_NOT_FOUND);
        return tenantSeq;
    }
}
