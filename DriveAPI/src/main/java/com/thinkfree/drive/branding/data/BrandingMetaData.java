package com.thinkfree.drive.branding.data;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 브랜딩 메타(이미지 제외) — 구 BrandingMapper.findMeta. JPQL 생성자 식({@code select new …BrandingMetaData(b.brandingSeq, b.tenantSeq, b.tabTitle, b.serviceName, b.useDefault, b.updateDate, b.registerDate)})이 쓰는 생성자 —
 * 인자 순서를 바꾸면 {@code BrandingJpaRepository.findMeta} 의 JPQL 도 함께. useDefault 는 엔티티의 Y/N 컨버터 결과(Boolean)를 받아 primitive 로 보관.
 */
@Getter
@NoArgsConstructor
public class BrandingMetaData {
    private Integer brandingSeq;
    private Integer tenantSeq;
    private String tabTitle;
    private String serviceName;
    private boolean useDefault;
    private Instant updateDate;
    private Instant registerDate;

    public BrandingMetaData(Integer brandingSeq, Integer tenantSeq, String tabTitle, String serviceName, Boolean useDefault, Instant updateDate, Instant registerDate) {
        this.brandingSeq = brandingSeq;
        this.tenantSeq = tenantSeq;
        this.tabTitle = tabTitle;
        this.serviceName = serviceName;
        this.useDefault = Boolean.TRUE.equals(useDefault);
        this.updateDate = updateDate;
        this.registerDate = registerDate;
    }
}
