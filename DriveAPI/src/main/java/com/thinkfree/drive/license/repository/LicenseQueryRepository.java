package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 라이선스 읽기(구 LicenseMapper 어노테이션 @Select 4종) — {@code LicenseService}·{@code LicenseInitializer} 가 주입. 쓰기(upsert)는 {@link LicenseRepository}.
 */
public interface LicenseQueryRepository {

    /**
     * 테넌트의 라이선스 행 — 키 {@code tenant_seq}(Integer)·{@code data}(byte[])·{@code created_at}·{@code updated_at}(Instant — {@code LicenseService} 캐시의 updated_at 동등 비교용). 없으면 null.
     */
    Map<String, Object> findLicense(int tenantSeq);

    /** 테넌트 라이선스의 updated_at. 행이 없거나 NULL 이면 null(= 미등록 판정). */
    Instant findUpdatedAt(int tenantSeq);

    /** 캐시가 최신인지 판단하는 값(파일 수정 시각 + 온라인 인증 시각). 행이 없으면 null. */
    LicenseVersion findLicenseVersion(int tenantSeq);

    /** 온라인 인증에 필요한 라이선스 파일과 직전 인증 상태. 행이 없으면 null. */
    LicenseAuthStateData findAuthState(int tenantSeq);

    /** 라이선스가 등록된 테넌트 seq 전부(기동 시 캐시 warm-up). */
    List<Integer> findAllLicensedTenantSeqs();

    /** 온라인 인증 라이선스가 등록된 테넌트 seq 전부(기동·일일 인증 대상). */
    List<Integer> findOnlineLicensedTenantSeqs();

    /** DB 서버 현재 시각(epoch millis). 라이선스 만료 판정 기준 시각으로 쓴다. */
    Long getServerTimeMS();
}
