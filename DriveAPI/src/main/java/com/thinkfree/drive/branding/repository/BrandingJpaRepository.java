package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.entity.Branding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * tb_branding Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultBrandingQueryRepository} 만 주입받아 위임한다.
 * 메타·이미지 조회는 JPA(JPQL 생성자 식 — 필요한 컬럼만 읽음, 이미지는 종류별 bytea 1개). upsert 2종은 JdbcClient(Default 리포).
 */
interface BrandingJpaRepository extends JpaRepository<Branding, Integer> {

    @Query("select new com.thinkfree.drive.branding.data.BrandingMetaData(b.brandingSeq, b.tenantSeq, b.tabTitle, b.serviceName, b.useDefault, b.updateDate, b.registerDate) "
            + "from Branding b where b.tenantSeq = :tenantSeq")
    Optional<BrandingMetaData> findMeta(@Param("tenantSeq") Integer tenantSeq);

    @Query("select new com.thinkfree.drive.branding.data.BrandingImageData(b.faviconData, b.faviconContentType) from Branding b where b.tenantSeq = :tenantSeq")
    Optional<BrandingImageData> findFavicon(@Param("tenantSeq") Integer tenantSeq);

    @Query("select new com.thinkfree.drive.branding.data.BrandingImageData(b.logoIconData, b.logoIconContentType) from Branding b where b.tenantSeq = :tenantSeq")
    Optional<BrandingImageData> findLogoIcon(@Param("tenantSeq") Integer tenantSeq);

    @Query("select new com.thinkfree.drive.branding.data.BrandingImageData(b.logoImageData, b.logoImageContentType) from Branding b where b.tenantSeq = :tenantSeq")
    Optional<BrandingImageData> findLogoImage(@Param("tenantSeq") Integer tenantSeq);

    @Query("select new com.thinkfree.drive.branding.data.BrandingImageData(b.emailLogoData, b.emailLogoContentType) from Branding b where b.tenantSeq = :tenantSeq")
    Optional<BrandingImageData> findEmailLogo(@Param("tenantSeq") Integer tenantSeq);
}
