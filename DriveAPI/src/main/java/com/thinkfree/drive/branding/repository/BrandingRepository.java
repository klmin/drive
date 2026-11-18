package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.entity.Branding;

/**
 * tb_branding 쓰기 — {@code DefaultBrandingService.upsert}(관리자 브랜딩 저장) 가 주입. 읽기는 {@link BrandingQueryRepository}.
 * 두 upsert 모두 등록 감사가 비어 있으면 컨텍스트 사용자·현재 시각으로 채우고(충돌 시 그 값이 update_* 에도 들어감, 구 XML 동형) 엔티티에 반영한다.
 */
public interface BrandingRepository {

    /** 텍스트 + 이미지 4종 전부 INSERT 또는 (tenant_seq 충돌 시) 갱신 */
    void upsertWithImages(Branding entity);

    /** 텍스트(tab_title·service_name·use_default)만 INSERT 또는 갱신 — 기존 이미지 컬럼은 손대지 않음 */
    void upsertTextOnly(Branding entity);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}
