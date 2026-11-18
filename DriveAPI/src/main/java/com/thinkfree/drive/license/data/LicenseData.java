package com.thinkfree.drive.license.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 테넌트 라이선스 행 — 읽기 전용 값(라이선스 검증·캐시). getter 이름은 엔티티 {@code License} 와 같다. */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(LicenseJpaRepository#findDataByTenantSeq)
public class LicenseData {

    private Integer tenantSeq;
    private byte[] data;
    private Instant createdAt;
    private Instant updatedAt;
}
