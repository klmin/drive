package com.thinkfree.drive.superadmin.loginsetting.service;

import com.thinkfree.drive.superadmin.loginsetting.converter.SuperAdminLoginSettingConverter;
import com.thinkfree.drive.superadmin.loginsetting.dto.SuperAdminLoginSettingUpsertDto;
import com.thinkfree.drive.superadmin.loginsetting.repository.SuperAdminLoginSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultSuperAdminLoginSettingService implements SuperAdminLoginSettingService {

    private final SuperAdminLoginSettingRepository repository;
    private final SuperAdminLoginSettingConverter superAdminLoginSettingConverter;

    @Override
    public void upsert(SuperAdminLoginSettingUpsertDto dto) {
        var entity = superAdminLoginSettingConverter.toUpsertEntity(dto);
        repository.upsert(entity);
    }

    @Override
    public void upsertEnforce(boolean isEnforced) {
        var entity = superAdminLoginSettingConverter.toEnforceUpsertEntity(isEnforced);
        repository.upsertEnforce(entity);
    }
}
