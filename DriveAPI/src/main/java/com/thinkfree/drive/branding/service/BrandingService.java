package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.dto.BrandingUpsertDto;


public interface BrandingService {

     void upsert(BrandingUpsertDto dto);
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    void deleteByTenant(Integer tenantSeq);
}
