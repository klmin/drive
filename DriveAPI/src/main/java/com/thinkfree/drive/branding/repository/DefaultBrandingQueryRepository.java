package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.query.BrandingImageFindQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * tb_branding 읽기 — 구 BrandingMapper.xml 의 findMeta·findBrandingImage 를 JPA JPQL 생성자 식으로(§1 v2, 이미지는 10-08 JdbcClient → JPQL). 쓰기는 {@link DefaultBrandingRepository}.
 * <ul>
 *   <li>{@code findMeta} = JPQL 생성자 식(DTO 투영이라 detach 불필요, bytea 4개를 읽지 않음)</li>
 *   <li>{@code findBrandingImage} = 종류별 JPQL 4벌(구 XML {@code <choose type>} 의 컬럼 쌍 선택) — 그 종류의 bytea·content type 2컬럼만 읽는다.
 *       <b>행은 있으나 그 종류의 이미지·content type 이 둘 다 NULL 이면 null</b> — 구 MyBatis 가 전 컬럼 NULL 행을 null 객체로 돌려주던 계약(FindService 가 BRANDING_NOT_FOUND 로 변환) 유지</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultBrandingQueryRepository implements BrandingQueryRepository {

    private final BrandingJpaRepository jpaRepository;

    @Override
    public BrandingMetaData findMeta(int tenantSeq) {
        return jpaRepository.findMeta(tenantSeq).orElse(null);
    }

    @Override
    public BrandingImageData findBrandingImage(BrandingImageFindQuery query) {
        return findImage(query.getType(), query.getTenantSeq())
                .filter(image -> image.getImageData() != null || image.getContentType() != null)
                .orElse(null);
    }

    private Optional<BrandingImageData> findImage(BrandingImageType type, Integer tenantSeq) {
        return switch (type) {
            case FAVICON -> jpaRepository.findFavicon(tenantSeq);
            case LOGO_ICON -> jpaRepository.findLogoIcon(tenantSeq);
            case LOGO_IMAGE -> jpaRepository.findLogoImage(tenantSeq);
            case EMAIL_LOGO -> jpaRepository.findEmailLogo(tenantSeq);
        };
    }
}
