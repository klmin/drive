package com.thinkfree.drive.share.setting.service;

import com.thinkfree.drive.share.setting.converter.ShareSettingConverter;
import com.thinkfree.drive.share.setting.dto.ShareSettingUpsertDto;
import com.thinkfree.drive.share.setting.repository.ShareSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultShareSettingService implements ShareSettingService {

    private final ShareSettingRepository repository;
    private final ShareSettingConverter converter;

    @Override
    public void upsertGlobal(ShareSettingUpsertDto dto) {
        var entity = converter.toEntity(converter.toGlobalDto(dto));
        repository.upsertGlobal(entity);
    }

    @Override
    public void upsertGlobalEnforce(boolean isEnforced) {
        var entity = converter.toEntity(converter.toGlobalEnforceDto(isEnforced));
        repository.upsertGlobalEnforce(entity);
    }

    @Override
    public void upsertGlobalShareOff() {
        var entity = converter.toEntity(converter.toGlobalEnforceDto(true));
        repository.upsertGlobalShareOff(entity);
    }

    @Override
    public void upsertTenant(ShareSettingUpsertDto dto) {
        var entity = converter.toEntity(converter.toTenantDto(dto, dto.getTenantSeq()));
        repository.upsertTenant(entity);
    }

    @Override
    public void upsertTenantShareOff(int tenantSeq) {
        var entity = converter.toEntity(converter.toTenantDefaultDto(tenantSeq));
        repository.upsertTenantShareOff(entity);
    }

    @Override
    public void initializeDefaultForTenant(Integer tenantSeq) {
        var entity = converter.toEntity(converter.toTenantDefaultDto(tenantSeq));
        repository.insert(entity);
    }
    @Override
    public void deleteByTenant(Integer tenantSeq) {
        repository.deleteByTenant(tenantSeq);
    }
}
