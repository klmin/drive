package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;

/** 읽기 전용(FindService 가 주입). 싱글턴 행(seq 1) 조회 — JPQL 생성자 식 구현. */
public interface SuperAdminLoginSettingQueryRepository {
    SuperAdminLoginSettingData find();
}
