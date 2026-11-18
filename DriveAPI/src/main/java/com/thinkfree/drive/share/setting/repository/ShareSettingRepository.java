package com.thinkfree.drive.share.setting.repository;

import com.thinkfree.drive.share.setting.entity.ShareSetting;

/** 쓰기(Service 가 주입). 읽기는 {@link ShareSettingQueryRepository}. */
public interface ShareSettingRepository {

    /** 슈퍼어드민(글로벌) 전 컬럼 upsert. 함수 유니크(tb_share_setting_uk_global)로 글로벌 1행 보장. (전체+공유 on) */
    void upsertGlobal(ShareSetting entity);

    /**
     * 슈퍼어드민(글로벌) 적용범위(is_enforced)만 upsert: 행 있으면 enforce 만 갱신(상세 flag 보존),
     * 없으면 entity 의 (기본) flag 로 신규 생성. 개별적용 저장에 사용.
     */
    void upsertGlobalEnforce(ShareSetting entity);

    /**
     * 슈퍼어드민(글로벌) enforce + external 만 upsert: 행 있으면 둘만 갱신(scope flag 보존),
     * 없으면 entity 의 (기본) flag 로 신규 생성. 전체적용 + 공유 off 저장에 사용.
     */
    void upsertGlobalShareOff(ShareSetting entity);

    /** 테넌트 행 upsert. */
    void upsertTenant(ShareSetting entity);

    /**
     * 테넌트 공유 off upsert: 행 있으면 external 만 N 으로 갱신(scope flag 보존),
     * 없으면 entity 의 (기본) flag 로 신규 생성. 테넌트 공유 off 저장에 사용.
     */
    void upsertTenantShareOff(ShareSetting entity);

    /** 테넌트 기본 행 신규 생성 (테넌트 생성 시점). */
    void insert(ShareSetting entity);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}
