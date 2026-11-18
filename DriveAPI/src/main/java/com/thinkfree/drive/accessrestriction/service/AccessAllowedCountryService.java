package com.thinkfree.drive.accessrestriction.service;

import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.component.CountryCodeRegistry;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedCountryRepository;
import com.thinkfree.drive.accessrestriction.repository.AccessAllowedCountryQueryRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessAllowedCountryService {

    private final AccessAllowedCountryRepository accessAllowedCountryRepository;
    private final AccessAllowedCountryQueryRepository accessAllowedCountryQueryRepository;
    private final CountryCodeRegistry countryCodeRegistry;

    @Transactional
    public void replaceAllowedCountries(Integer tenantSeq, Integer loginUserSeq, List<String> countries) {
        countries.forEach(code -> {
            if (!countryCodeRegistry.isValid(code)) {
                throw ApiRuntimeException.type(AccessErrorCode.INVALID_COUNTRY_CODE)
                        .with("countryCode", code);
            }
        });
        accessAllowedCountryRepository.deleteByTenant(tenantSeq);
        if (!countries.isEmpty()) {
            var entities = countries.stream()
                    .map(code -> AccessAllowedCountry.create(tenantSeq, code, loginUserSeq))
                    .toList();
            accessAllowedCountryRepository.insertAll(entities);
        }
    }

    @Transactional(readOnly = true)
    public List<String> findAllCountryCodes(Integer tenantSeq) {
        return accessAllowedCountryQueryRepository.findAllCountryCodes(tenantSeq);
    }
    
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    public void deleteByTenant(Integer tenantSeq) {
        accessAllowedCountryRepository.deleteByTenant(tenantSeq);
    }
}
