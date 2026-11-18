package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.query.BrandingImageFindQuery;

/**
 * tb_branding 읽기 — {@code DefaultBrandingFindService}(관리자 설정 조회·오픈 이미지 API·서비스명) 가 주입. 쓰기는 {@link BrandingRepository}.
 */
public interface BrandingQueryRepository {

    /** 테넌트의 브랜딩 메타(이미지 제외), 없으면 null */
    BrandingMetaData findMeta(int tenantSeq);

    /** 종류별 이미지 1건(bytea + content type). 행이 없거나 그 종류의 이미지가 비어 있으면 null(구 MyBatis 가 전 컬럼 NULL 행을 null 로 돌려주던 계약) */
    BrandingImageData findBrandingImage(BrandingImageFindQuery query);
}
