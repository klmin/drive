package com.thinkfree.drive.superadmin.loginsetting.service;

import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.repository.SuperAdminLoginSettingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultSuperAdminLoginSettingFindService implements SuperAdminLoginSettingFindService {

    private final SuperAdminLoginSettingQueryRepository repository;

    @Override
    public SuperAdminLoginSettingData find() {
        return repository.find();
    }
}
