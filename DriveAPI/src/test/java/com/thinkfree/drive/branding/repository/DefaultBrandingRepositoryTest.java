package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.entity.Branding;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.query.BrandingImageFindQuery;
import com.thinkfree.drive.common.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 Branding 이관 회귀 — §1 v2: 메타 조회 JPA(JPQL 생성자 식·Y/N 컨버터), 이미지 조회·upsert 2종 JdbcClient({@link DefaultBrandingQueryRepository}·{@link DefaultBrandingRepository}).
 * tb_branding 에 FK 없음(tenant_seq·user_seq 임의 값, UNIQUE tenant_seq). 트랜잭션 롤백.
 * 검증 포인트 = upsertTextOnly INSERT(등록 감사 비면 write-back·이미지 NULL·use_default N), findMeta 7필드·없는 테넌트 null, findBrandingImage 의 "이미지 없음 → null" 계약, upsertWithImages 충돌(텍스트·이미지 4종 갱신, register_* 유지, update_* = 바인딩 값),
 * 종류별 이미지 bytea 왕복, upsertTextOnly 충돌이 이미지를 보존, 테넌트 격리, 엔티티 감사 값 우선.
 */
@DisplayName("DefaultBrandingRepository / DefaultBrandingQueryRepository — JPA 메타 + JdbcClient 이미지·upsert(PostgreSQL Testcontainers)")
class DefaultBrandingRepositoryTest extends RepositoryTest {

    private static final int TENANT_A = 910001;
    private static final int TENANT_B = 910002;
    private static final int ADMIN_A = 920001;
    private static final int ADMIN_B = 920002;

    @Autowired
    private BrandingRepository repository;
    @Autowired
    private BrandingQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private BrandingImageData image(int tenantSeq, BrandingImageType type) {
        return queryRepository.findBrandingImage(BrandingImageFindQuery.builder().tenantSeq(tenantSeq).type(type).build());
    }

    private Map<String, Object> row(int tenantSeq) {
        return jdbc.queryForMap("SELECT use_default, register_user_seq, register_date, update_user_seq, update_date, favicon_content_type FROM tb_branding WHERE tenant_seq = ?", tenantSeq);
    }

    @Test
    @DisplayName("upsertTextOnly(INSERT: 감사 write-back·이미지 NULL·N) → findMeta(JPQL·Y/N) / 이미지 없음 → null → upsertWithImages(충돌: 텍스트·이미지 4종·update_* 갱신, register_* 유지) → 종류별 bytea 왕복 → upsertTextOnly(충돌)는 이미지 보존 / 테넌트 격리")
    void upsertAndFind() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(queryRepository.findMeta(TENANT_A)).isNull();
        assertThat(image(TENANT_A, BrandingImageType.FAVICON)).as("행 없음 → null").isNull();

        // INSERT(텍스트만) — 등록 감사 비면 채움 + write-back, 이미지 컬럼 NULL
        Branding first = Branding.builder().tenantSeq(TENANT_A).tabTitle("Tab A").serviceName("Svc A").useDefault(false).registerUserSeq(ADMIN_A).build();
        repository.upsertTextOnly(first);
        assertThat(first.getRegisterUserSeq()).as("엔티티 값 우선(SecurityContext 없는 테스트라 register_user_seq NOT NULL 은 명시)").isEqualTo(ADMIN_A);
        assertThat(first.getRegisterDate()).as("비면 현재 시각 write-back").isNotNull().isAfterOrEqualTo(before);
        Map<String, Object> inserted = row(TENANT_A);
        assertThat(inserted.get("use_default")).isEqualTo("N");
        assertThat(inserted.get("update_user_seq")).isNull();
        assertThat(inserted.get("update_date")).isNull();
        assertThat(inserted.get("favicon_content_type")).isNull();
        BrandingMetaData meta = queryRepository.findMeta(TENANT_A);
        assertThat(meta).isNotNull();
        assertThat(meta.getBrandingSeq()).as("identity 키").isNotNull().isPositive();
        assertThat(meta.getTenantSeq()).isEqualTo(TENANT_A);
        assertThat(meta.getTabTitle()).isEqualTo("Tab A");
        assertThat(meta.getServiceName()).isEqualTo("Svc A");
        assertThat(meta.isUseDefault()).as("Y/N 컨버터 → false").isFalse();
        assertThat(meta.getRegisterDate()).isCloseTo(first.getRegisterDate(), within(1, ChronoUnit.MICROS));
        assertThat(meta.getUpdateDate()).isNull();
        assertThat(image(TENANT_A, BrandingImageType.FAVICON)).as("행은 있으나 이미지·타입 NULL → null(구 MyBatis 계약)").isNull();

        // 충돌(이미지 포함) — 엔티티 감사 값 우선, register_* 유지, update_* = 바인딩 값
        Instant at = Instant.parse("2026-02-02T02:02:02Z");
        Branding second = Branding.builder().tenantSeq(TENANT_A).tabTitle("Tab A2").serviceName("Svc A2").useDefault(true)
                .faviconData(bytes("fav")).faviconContentType("image/x-icon")
                .logoIconData(bytes("icon")).logoIconContentType("image/svg+xml")
                .logoImageData(bytes("logo")).logoImageContentType("image/png")
                .emailLogoData(bytes("mail")).emailLogoContentType("image/png")
                .registerUserSeq(ADMIN_B).registerDate(at).build();
        repository.upsertWithImages(second);
        assertThat(second.getRegisterUserSeq()).isEqualTo(ADMIN_B);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_branding WHERE tenant_seq = ?", Integer.class, TENANT_A)).isEqualTo(1);
        Map<String, Object> updated = row(TENANT_A);
        assertThat(updated.get("use_default")).isEqualTo("Y");
        assertThat(updated.get("update_user_seq")).as("충돌 시 update_user_seq = 바인딩한 register_user_seq(구 XML 동형)").isEqualTo(ADMIN_B);
        assertThat(((java.sql.Timestamp) updated.get("update_date")).toInstant()).isEqualTo(at);
        assertThat(((java.sql.Timestamp) updated.get("register_date")).toInstant()).as("register_date 유지").isCloseTo(first.getRegisterDate(), within(1, ChronoUnit.MICROS));
        BrandingMetaData meta2 = queryRepository.findMeta(TENANT_A);
        assertThat(meta2.getBrandingSeq()).isEqualTo(meta.getBrandingSeq());
        assertThat(meta2.getTabTitle()).isEqualTo("Tab A2");
        assertThat(meta2.isUseDefault()).isTrue();
        assertThat(meta2.getUpdateDate()).isEqualTo(at);
        assertThat(image(TENANT_A, BrandingImageType.FAVICON).getImageData()).isEqualTo(bytes("fav"));
        assertThat(image(TENANT_A, BrandingImageType.FAVICON).getContentType()).isEqualTo("image/x-icon");
        assertThat(image(TENANT_A, BrandingImageType.LOGO_ICON).getImageData()).isEqualTo(bytes("icon"));
        assertThat(image(TENANT_A, BrandingImageType.LOGO_IMAGE).getContentType()).isEqualTo("image/png");
        assertThat(image(TENANT_A, BrandingImageType.EMAIL_LOGO).getImageData()).isEqualTo(bytes("mail"));

        // 충돌(텍스트만) — 이미지 보존
        repository.upsertTextOnly(Branding.builder().tenantSeq(TENANT_A).tabTitle("Tab A3").serviceName("Svc A3").useDefault(false).registerUserSeq(ADMIN_A).build());
        assertThat(queryRepository.findMeta(TENANT_A).getTabTitle()).isEqualTo("Tab A3");
        assertThat(row(TENANT_A).get("update_user_seq")).isEqualTo(ADMIN_A);
        assertThat(image(TENANT_A, BrandingImageType.FAVICON).getImageData()).as("텍스트 upsert 는 이미지를 손대지 않음").isEqualTo(bytes("fav"));

        // 테넌트 격리
        repository.upsertTextOnly(Branding.builder().tenantSeq(TENANT_B).tabTitle("Tab B").serviceName("Svc B").useDefault(true).registerUserSeq(ADMIN_A).build());
        assertThat(queryRepository.findMeta(TENANT_B).getTabTitle()).isEqualTo("Tab B");
        assertThat(queryRepository.findMeta(TENANT_A).getTabTitle()).isEqualTo("Tab A3");
        assertThat(image(TENANT_B, BrandingImageType.FAVICON)).isNull();
        assertThat(queryRepository.findMeta(999999)).isNull();
    }
}
