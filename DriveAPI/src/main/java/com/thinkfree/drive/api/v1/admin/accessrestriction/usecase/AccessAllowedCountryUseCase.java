package com.thinkfree.drive.api.v1.admin.accessrestriction.usecase;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedCountriesUpdateRequest;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessAllowedCountryUseCase {

    private final AccessAllowedCountryService accessAllowedCountryService;

    @TenantFeatureRequired(TenantFeature.ACCESS_RESTRICTION)
    public void putAllowedCountries(AllowedCountriesUpdateRequest request, int requestUserSeq) {
        accessAllowedCountryService.replaceAllowedCountries(TenantContext.getOrThrow(), requestUserSeq, request.countries());
    }
}
