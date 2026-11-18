package com.thinkfree.drive.superadmin.loginsetting.converter;

import com.thinkfree.drive.superadmin.loginsetting.dto.SuperAdminLoginSettingUpsertDto;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SuperAdminLoginSettingConverter {

    SuperAdminLoginSetting toUpsertEntity(SuperAdminLoginSettingUpsertDto dto);

    default SuperAdminLoginSetting toEnforceUpsertEntity(boolean isEnforced) {
        return SuperAdminLoginSetting.builder()
                                     .isEnforced(isEnforced)
                                     .build();
    }
}
