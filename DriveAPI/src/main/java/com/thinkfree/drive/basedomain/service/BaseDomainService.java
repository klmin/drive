package com.thinkfree.drive.basedomain.service;

import com.thinkfree.drive.basedomain.result.BaseDomainUpsertResult;

public interface BaseDomainService {

    /**
     * 베이스 도메인 등록/수정 (현재 단일 — seq=1 고정 upsert).
     * 정규화/검증 후 seq=1, 정규화된 base_domain 을 결과로 반환한다.
     */
    BaseDomainUpsertResult upsert(String baseDomain);
}
