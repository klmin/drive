package com.thinkfree.drive.sso.provider.service;

import com.thinkfree.drive.sso.provider.dto.SsoProviderUpsertDto;

import java.util.List;

public interface SsoProviderService {
    void syncJwt(SsoProviderUpsertDto dto);
    void syncOidcList(int tenantSeq, List<SsoProviderUpsertDto> dtoList);
    void syncGlobalJwt(SsoProviderUpsertDto dto);
    void syncGlobalOidcList(List<SsoProviderUpsertDto> dtoList);
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    void deleteByTenant(Integer tenantSeq);
}
