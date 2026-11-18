package com.thinkfree.drive.api.v1.superadmin.tenant.converter;

import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantDetailResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantListResponse;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.license.result.LicenseSummary;
import com.thinkfree.drive.tenant.data.TenantData;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {DriveConstants.class})
public interface SuperAdminTenantResponseConverter {

    @Mapping(target = "license", expression = "java(toLicenseInfo(summary))")
    @Mapping(target = "office", source = "office")
    SuperAdminTenantListResponse toListResponse(TenantData tenant, String clientUrl,
                                                SuperAdminTenantListResponse.OfficeInfo office, @Context LicenseSummary summary);

    SuperAdminTenantDetailResponse toDetailResponse(TenantData tenant, List<String> allowedIps, Long totalQuotaByte);

    default SuperAdminTenantListResponse.LicenseInfo toLicenseInfo(LicenseSummary summary) {
        if (summary == null) {
            return null;
        }
        SuperAdminTenantListResponse.SeatInfo seatInfo = null;
        if (summary.seat() != null) {
            seatInfo = new SuperAdminTenantListResponse.SeatInfo(
                    summary.seat().current(),
                    summary.seat().max()
            );
        }
        return new SuperAdminTenantListResponse.LicenseInfo(
                summary.state(),
                summary.expiresOn(),
                seatInfo
        );
    }
}
