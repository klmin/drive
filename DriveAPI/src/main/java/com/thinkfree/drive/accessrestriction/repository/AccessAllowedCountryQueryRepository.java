package com.thinkfree.drive.accessrestriction.repository;

import java.util.List;

/**
 * tb_access_allowed_country 읽기 — {@code AccessAllowedCountryService.findAllCountryCodes}(IpAccessControlService·관리자 설정 조회) 가 주입. 쓰기는 {@link AccessAllowedCountryRepository}.
 */
public interface AccessAllowedCountryQueryRepository {

    /** 테넌트의 허용 국가 코드 전부 — country_iso_code 오름차순 */
    List<String> findAllCountryCodes(Integer tenantSeq);
}
