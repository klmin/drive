package com.thinkfree.drive.share.setting.repository;

import com.thinkfree.drive.share.setting.data.ShareSettingData;

/** 읽기 전용(FindService 가 주입). {@link ShareSettingData} 조회 — JPQL 생성자 식 구현. */
public interface ShareSettingQueryRepository {
    ShareSettingData findGlobal();
    ShareSettingData findByTenant(int tenantSeq);
}
