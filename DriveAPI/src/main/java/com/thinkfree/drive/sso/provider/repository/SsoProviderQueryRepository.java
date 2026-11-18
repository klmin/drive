package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.query.*;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 테넌트/글로벌 범위의 목록·단건·존재 검사 — DTO 만 반환. 쓰기(upsert·IN 삭제)는 {@link SsoProviderRepository}.
 * 소유 판정(범위·테넌트)은 단건 {@link #findById} + Java 판정(W4 — 별도 exists 메서드 없음).
 */
public interface SsoProviderQueryRepository {

    /** 테넌트 범위 목록 — 테넌트·프로토콜 선택 조건, 커서(sso_provider_seq &lt; cursorId)·LIMIT 는 page 가 있을 때만, seq DESC */
    List<SsoProviderData> findAll(SsoProviderListQuery query);

    /** 글로벌 범위 목록 — 프로토콜 선택 조건, sort_order ASC·seq DESC(커서 없음) */
    List<SsoProviderData> findAllGlobal(SsoProviderGlobalListQuery query);

    /** 단건(테넌트 LEFT JOIN — 글로벌이면 tenantName null). 없으면 null */
    SsoProviderData findById(SsoProviderFindQuery query);

    /** 테넌트 범위에서 (tenant, protocol, status) 1건 — 기본 9컬럼(감사 없음). 없으면 null */
    SsoProviderData findActiveByTenantAndProtocol(SsoProviderByProtocolQuery query);

    /** 글로벌 범위에서 (protocol, status) 1건(LIMIT 1) — 기본 9컬럼. 없으면 null */
    SsoProviderData findActiveByGlobalAndProtocol(SsoProviderGlobalByProtocolQuery query);

    boolean existsActiveSingleType(SsoProviderActiveCheckQuery query);

    boolean existsActiveSingleTypeByGlobal(SsoProviderGlobalActiveCheckQuery query);

    /** 같은 범위(scope + tenant, GLOBAL 은 tenant IS NULL) 안의 표시 이름 존재 — uniq_sso_provider_scope_tenant_name 과 같은 키, 자기 자신(excludeSsoProviderSeq) 제외 가능 */
    boolean existsDisplayName(SsoProviderDisplayNameCheckQuery query);
}
