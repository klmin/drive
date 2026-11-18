package com.thinkfree.drive.branding.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

/**
 * tb_branding — 테넌트별 브랜딩(탭 제목·서비스명·이미지 4종 bytea). 테넌트당 1행(UNIQUE tenant_seq), 쓰기는 전부 upsert.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선"): 메타 조회는 JPQL 생성자 식({@code BrandingMetaData}) — {@code BrandingJpaRepository}. **쓰기 2종(upsert)과 이미지 1종 조회는 JdbcClient**
 * ({@code ON CONFLICT (tenant_seq)} 는 JPQL 로 표현 불가, 이미지는 종류별 컬럼 쌍만 읽어야 해서 enum 유도 컬럼 상수 — bytea 4개를 전부 로드하는 엔티티 조회는 낭비).
 * use_default 는 char(1) Y/N → {@link YesNoConverter}. show_powered_by 컬럼은 구 매퍼도 다루지 않아 매핑하지 않음. 변경 메서드 없음, upsert 감사 write-back 은 {@link #assignRegisterAudit}.
 * MapStruct {@code BrandingConverter} 가 Builder 로 만든다.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_branding")
public class Branding extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer brandingSeq;
    private Integer tenantSeq;
    private String tabTitle;
    private String serviceName;
    @Convert(converter = YesNoConverter.class)
    private Boolean useDefault;
    private byte[] faviconData;
    private String faviconContentType;
    private byte[] logoIconData;
    private String logoIconContentType;
    private byte[] logoImageData;
    private String logoImageContentType;
    private byte[] emailLogoData;
    private String emailLogoContentType;
}
