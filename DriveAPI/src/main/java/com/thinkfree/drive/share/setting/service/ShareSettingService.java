package com.thinkfree.drive.share.setting.service;

import com.thinkfree.drive.share.setting.dto.ShareSettingUpsertDto;

public interface ShareSettingService {

    /** 슈퍼어드민(글로벌) 공유 설정 등록/수정. (적용범위 enforce 포함) */
    void upsertGlobal(ShareSettingUpsertDto dto);

    /**
     * 슈퍼어드민(글로벌) 적용범위(enforce)만 등록/수정. 기존 상세 flag 는 보존한다.
     * 개별적용(enforce=false) 저장 시 사용 — 화면에 안 뜨는 상세 flag 를 덮어쓰지 않기 위함.
     * (글로벌 행이 아예 없을 때만 기본 flag: external=N, specific=Y, orgLink=Y, public=N 으로 신규 생성)
     */
    void upsertGlobalEnforce(boolean isEnforced);

    /**
     * 슈퍼어드민(글로벌) 전체적용 + 공유 off 등록/수정. enforce=Y 로 두고 외부공유(external)만 차단,
     * scope 권한(specific/orgLink/public)은 보존한다 — 재활성 대비.
     * (글로벌 행이 아예 없을 때만 enforce=Y + 기본 flag: external=N, specific=Y, orgLink=Y, public=N 으로 신규 생성)
     */
    void upsertGlobalShareOff();

    /** 테넌트별 공유 설정 등록/수정. (enforce 는 항상 N 강제) */
    void upsertTenant(ShareSettingUpsertDto dto);

    /**
     * 테넌트 공유 off 등록/수정. 외부공유(external)만 차단, scope 권한(specific/orgLink/public)은 보존한다 — 재활성 대비.
     * (테넌트 행이 없을 때만 external=N + 기본 flag: specific=Y, orgLink=Y, public=N 으로 신규 생성)
     */
    void upsertTenantShareOff(int tenantSeq);

    /** 테넌트 생성 시점 기본 공유 설정 행 생성 (external=N, specific=Y, orgLink=Y, public=N). */
    void initializeDefaultForTenant(Integer tenantSeq);
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    void deleteByTenant(Integer tenantSeq);
}
