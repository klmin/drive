package com.thinkfree.drive.api.v1.superadmin.tenant.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.dto.TenantUpdateDto;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.query.TenantListQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class})
public interface SuperAdminTenantRequestConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(pagingRequest.cv(), pagingRequest.ci(), pagingRequest.ct(), pagingRequest.ps()))")
    TenantListQuery toTenantListQuery(CursorPagingRequest pagingRequest, TenantType tenantType);

    @Mapping(target = "tenantName", source = "request.tn")
    TenantSaveDto toSaveDto(SuperAdminTenantCreateRequest request, String subdomain, Integer baseDomainSeq, String domain,
                            TenantType tenantType, TenantUserMode userMode);

    @Mapping(target = "tenantName", source = "request.tn")
    TenantUpdateDto toUpdateDto(Integer tenantSeq, SuperAdminTenantUpdateRequest request, String subdomain, Integer baseDomainSeq, String domain);
}
