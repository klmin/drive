package com.thinkfree.drive.superadmin.loginsetting.data;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;

import java.time.Instant;

/**
 * tb_super_admin_login_setting 조회 결과(JPQL 생성자 식이 생성 — Boolean 을 받는 보조 생성자).
 * is_enforced 컬럼은 NOT NULL 이라 원시 boolean — 접근자 {@code isEnforced()} 는 기존 클래스 시절과 같은 이름·의미.
 */
public record SuperAdminLoginSettingData(
        Integer superAdminLoginSettingSeq,
        LoginType loginType,
        boolean isEnforced,
        Integer registerUserSeq,
        Instant registerDate,
        Integer updateUserSeq,
        Instant updateDate
) {

    /** JPQL 생성자 식용 — 엔티티의 Boolean(YesNoConverter) is_enforced 를 원시 boolean 으로(NOT NULL 컬럼, null 이면 false — 구 변환과 같음). */
    public SuperAdminLoginSettingData(Integer superAdminLoginSettingSeq, LoginType loginType, Boolean isEnforced,
                                      Integer registerUserSeq, Instant registerDate, Integer updateUserSeq, Instant updateDate) {
        this(superAdminLoginSettingSeq, loginType, Boolean.TRUE.equals(isEnforced), registerUserSeq, registerDate, updateUserSeq, updateDate);
    }
}
