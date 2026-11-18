package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;

import java.util.List;

/** 읽기 전용(FindService 가 주입). {@link ApiKeyData} 조회 — 전부 JPQL 생성자 식. 쓰기는 {@link ApiKeyRepository}. */
public interface ApiKeyQueryRepository {

    /** 관리자 목록 — 자기 테넌트의 ADMIN·USER role 키. registerUserSeq 를 주면 본인 발급 키로 좁힌다 */
    List<ApiKeyData> findAll(ApiKeyListQuery query);
    /** 사용자 목록 — 본인이 발급한 키 */
    List<ApiKeyData> findAllByRegisterUser(ApiKeyListQuery query);
    /** 슈퍼관리자 목록 — 전체. tenantSeq·registerUserSeq 를 주면 그 조건으로 좁힌다 */
    List<ApiKeyData> findAllForSuperAdmin(ApiKeyListQuery query);
    /** PK 단건(13컬럼, 상태·테넌트 조건 없음 — 판정은 호출부 Java). 없으면 null */
    ApiKeyData findById(int apiKeySeq);
    /** 인증용 — 같은 테넌트(또는 SUPER_ADMIN role) · ACTIVE · 미만료(DB NOW()) */
    ApiKeyData findActiveByApiKey(String apiKey, Integer tenantSeq);
}
