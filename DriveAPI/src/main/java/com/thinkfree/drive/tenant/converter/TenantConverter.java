package com.thinkfree.drive.tenant.converter;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {TenantStatus.class, LoginType.class})
public interface TenantConverter {

    @Mapping(target = "status", expression = "java(TenantStatus.ACTIVE)")
    @Mapping(target = "loginType", expression = "java(LoginType.LOCAL)")
    Tenant toSaveTenant(TenantSaveDto dto);

}
