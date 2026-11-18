package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;

import java.util.List;

/**
 * tb_access_allowed_country 쓰기 — {@code AccessAllowedCountryService.replaceAllowedCountries}(테넌트 전체 교체: 삭제 → N건 INSERT) 가 주입. 읽기는 {@link AccessAllowedCountryQueryRepository}.
 */
public interface AccessAllowedCountryRepository {

    void deleteByTenant(Integer tenantSeq);

    /** JDBC 배치 INSERT(생성 키 없음). 등록 감사가 비어 있으면 컨텍스트 사용자·현재 시각으로 채우고 엔티티에 반영. 빈/널 목록은 SQL 없이 끝 */
    void insertAll(List<AccessAllowedCountry> list);
}
