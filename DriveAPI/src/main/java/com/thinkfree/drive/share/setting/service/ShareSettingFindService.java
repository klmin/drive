package com.thinkfree.drive.share.setting.service;

import com.thinkfree.drive.share.setting.data.ShareSettingData;

public interface ShareSettingFindService {
    ShareSettingData findGlobal();
    ShareSettingData findByTenant(int tenantSeq);
}
