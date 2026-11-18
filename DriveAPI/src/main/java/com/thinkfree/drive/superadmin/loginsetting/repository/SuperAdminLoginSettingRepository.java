package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;

/** 쓰기(Service 가 주입). 읽기는 {@link SuperAdminLoginSettingQueryRepository}. */
public interface SuperAdminLoginSettingRepository {
    int upsert(SuperAdminLoginSetting entity);
    int upsertEnforce(SuperAdminLoginSetting entity);
}
