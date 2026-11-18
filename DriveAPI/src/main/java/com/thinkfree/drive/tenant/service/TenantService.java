package com.thinkfree.drive.tenant.service;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.dto.TenantUpdateDto;

public interface TenantService {

    int save(TenantSaveDto dto);
    void update(TenantUpdateDto dto);
    /** 테넌트 행 잠금(FOR UPDATE) — 호출 트랜잭션 끝까지 유지. 없는 테넌트면 TENANT_NOT_FOUND */
    void lock(int tenantSeq);
    /** 한 테넌트의 도메인 바인딩 갱신 — 이전 domain 이 비어 있었으면(새로 설정) true. 캐시 무효화는 호출부. */
    boolean rebindDomain(int tenantSeq, String subdomain, Integer baseDomainSeq, String domain);
    /** 베이스 도메인 등록·변경에 따른 테넌트 도메인 일괄 재바인딩. 캐시 무효화는 호출부. */
    void rebindToBaseDomain(int baseDomainSeq, String baseDomain, boolean firstBinding);
    void updateLoginType(int tenantSeq, LoginType loginType);
    /** 테넌트 행 삭제 — 없으면 TENANT_NOT_FOUND. 호스트 → 테넌트 캐시는 즉시·커밋 후 비운다(다른 노드는 캐시 TTL 까지 유지) */
    void delete(int tenantSeq);
}
