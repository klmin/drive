package com.thinkfree.drive.share.setting.service;

import com.thinkfree.drive.share.setting.data.ShareSettingData;
import com.thinkfree.drive.share.setting.repository.ShareSettingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultShareSettingFindService implements ShareSettingFindService {

    private final ShareSettingQueryRepository repository;

    @Override
    public ShareSettingData findGlobal() {
        return repository.findGlobal();
    }

    @Override
    public ShareSettingData findByTenant(int tenantSeq) {
        return repository.findByTenant(tenantSeq);
    }
}
