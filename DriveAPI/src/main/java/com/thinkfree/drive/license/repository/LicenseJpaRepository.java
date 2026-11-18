package com.thinkfree.drive.license.repository;

import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseData;
import com.thinkfree.drive.license.data.LicenseVersion;
import com.thinkfree.drive.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * tb_license Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultLicenseQueryRepository}(읽기)·{@link DefaultLicenseRepository}(쓰기) 만 주입받아 위임한다.
 * 읽기: 테넌트 단건(DTO 생성자 식)(UNIQUE tenant_seq 라 0/1건), updated_at 투영, 캐시 버전 투영, 라이선스 등록 테넌트 목록, 온라인 인증 라이선스 테넌트 목록.
 * 쓰기: 온라인 인증 결과 갱신은 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 {@code @Transactional} 필수). upsert 는 JdbcClient(Default 리포).
 */
interface LicenseJpaRepository extends JpaRepository<License, Integer> {

    /** 구 selectLicense — tenant_seq, data, created_at, updated_at. 생성자 식이라 엔티티를 영속 컨텍스트에 올리지 않는다. */
    @Query("select new com.thinkfree.drive.license.data.LicenseData(l.tenantSeq, l.data, l.createdAt, l.updatedAt) from License l where l.tenantSeq = :tenantSeq")
    Optional<LicenseData> findDataByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 구 selectUpdatedAt(LIMIT 1 — UNIQUE 라 불필요). 행은 있으나 updated_at 이 NULL 이면 empty. */
    @Query("select l.updatedAt from License l where l.tenantSeq = :tenantSeq")
    Optional<Instant> findUpdatedAtByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 캐시가 최신인지 판단하는 값 — 파일 수정 시각과 온라인 인증 시각. */
    @Query("select new com.thinkfree.drive.license.data.LicenseVersion(l.updatedAt, l.authCheckedAt) from License l where l.tenantSeq = :tenantSeq")
    Optional<LicenseVersion> findVersionByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 라이선스 파일과 저장된 온라인 인증 결과. */
    @Query("select new com.thinkfree.drive.license.data.LicenseAuthStateData(l.data, l.authStatus, l.authFailCount, l.authCheckedAt, l.authInitAt, l.authSuccessAt, l.authSignedData, l.updatedAt) "
            + "from License l where l.tenantSeq = :tenantSeq")
    Optional<LicenseAuthStateData> findAuthStateByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 구 selectAllLicensedTenantSeqs — tenant_seq IS NOT NULL(컬럼이 NOT NULL 이라 사실상 전부). */
    @Query("select l.tenantSeq from License l where l.tenantSeq is not null")
    List<Integer> findAllTenantSeqs();

    /** 온라인 라이선스는 등록할 때 인증 상태가 채워지고 오프라인 라이선스는 NULL 로 남는다. */
    @Query("select l.tenantSeq from License l where l.authStatus is not null")
    List<Integer> findOnlineTenantSeqs();

    /** 온라인 인증 결과만 기록한다. updated_at 은 파일이 바뀐 시각이므로 건드리지 않는다. null 로 넘긴 값은 기존 값을 둔다. */
    @Transactional
    @Modifying
    @Query("update License l set l.authStatus = :status, l.authFailCount = :failCount, l.authCheckedAt = :checkedAt, "
            + "l.authSuccessAt = coalesce(:successAt, l.authSuccessAt), l.authInitAt = coalesce(:initAt, l.authInitAt), "
            + "l.authSignedData = coalesce(:signedData, l.authSignedData) "
            + "where l.tenantSeq = :tenantSeq")
    int updateAuthResult(@Param("tenantSeq") Integer tenantSeq,
                         @Param("status") String status,
                         @Param("failCount") Integer failCount,
                         @Param("checkedAt") Instant checkedAt,
                         @Param("successAt") Instant successAt,
                         @Param("initAt") Instant initAt,
                         @Param("signedData") String signedData);

    /** {@link #updateAuthResult} 와 같되, 파일이 expectedUpdatedAt 에 저장된 그대로일 때만 기록한다. */
    @Transactional
    @Modifying
    @Query("update License l set l.authStatus = :status, l.authFailCount = :failCount, l.authCheckedAt = :checkedAt, "
            + "l.authSuccessAt = coalesce(:successAt, l.authSuccessAt), l.authInitAt = coalesce(:initAt, l.authInitAt), "
            + "l.authSignedData = coalesce(:signedData, l.authSignedData) "
            + "where l.tenantSeq = :tenantSeq and l.updatedAt = :expectedUpdatedAt")
    int updateAuthResultIfUnchanged(@Param("tenantSeq") Integer tenantSeq,
                                    @Param("status") String status,
                                    @Param("failCount") Integer failCount,
                                    @Param("checkedAt") Instant checkedAt,
                                    @Param("successAt") Instant successAt,
                                    @Param("initAt") Instant initAt,
                                    @Param("signedData") String signedData,
                                    @Param("expectedUpdatedAt") Instant expectedUpdatedAt);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from License l where l.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
