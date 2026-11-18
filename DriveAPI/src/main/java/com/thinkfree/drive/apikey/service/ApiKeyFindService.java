package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;

import java.util.List;

public interface ApiKeyFindService {

    /** 관리자 목록 — 자기 테넌트의 ADMIN·USER role 키 */
    List<ApiKeyData> findAll(ApiKeyListQuery query);
    /** 사용자 목록 — 본인이 발급한 키 */
    List<ApiKeyData> findAllByRegisterUser(ApiKeyListQuery query);
    /** 슈퍼관리자 목록 — 전체. tenantSeq 를 주면 그 테넌트로 좁힌다 */
    List<ApiKeyData> findAllForSuperAdmin(ApiKeyListQuery query);
    ApiKeyData findActiveByApiKey(String apiKey, Integer tenantSeq);
    /** 테넌트 경계 판정용(@TenantApiKey) — 키가 없거나 테넌트가 없는(슈퍼관리자) 키면 API_KEY_NOT_FOUND */
    Integer findTenantSeqByApiKeySeq(int apiKeySeq);
}
