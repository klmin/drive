package com.thinkfree.drive.share.setting.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.thinkfree.drive.share.setting.data.ShareSettingData;
import com.thinkfree.drive.share.setting.entity.ShareSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * tb_share_setting Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultShareSettingQueryRepository}(읽기 — JPQL 생성자 식)·{@link DefaultShareSettingRepository}(기본 행 save) 만 주입받아 위임한다.
 * upsert 5종은 JdbcClient(Default 리포).
 */
interface ShareSettingJpaRepository extends JpaRepository<ShareSetting, Integer> {

    /** 전 컬럼 DTO 선택 머리 — Boolean 플래그는 {@link ShareSettingData} 의 JPQL 생성자 식용 생성자가 Y/N 으로 바꾼다 */
    String SELECT_DATA = "select new com.thinkfree.drive.share.setting.data.ShareSettingData(s.shareSettingSeq, s.tenantSeq, "
            + "s.isEnforced, s.isExternalShareAllowed, s.isSpecificUsersAllowed, s.isOrgLinkAllowed, s.isPublicAllowed, "
            + "s.registerUserSeq, s.registerDate, s.updateUserSeq, s.updateDate) from ShareSetting s ";

    /** 글로벌 행 — 표현식 유니크 (COALESCE(tenant_seq, 0)) 라 0/1건 */
    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where s.tenantSeq is null")
    Optional<ShareSettingData> findGlobal();

    /** 테넌트 행 — tenant_seq 는 PK 가 아니지만 표현식 유니크라 0/1건 */
    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where s.tenantSeq = :tenantSeq")
    Optional<ShareSettingData> findByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from ShareSetting s where s.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
