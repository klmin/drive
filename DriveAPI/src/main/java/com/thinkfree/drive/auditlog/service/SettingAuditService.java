package com.thinkfree.drive.auditlog.service;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;

import java.util.List;

/**
 * SETTING 카테고리 감사 로그 기록의 단일 진입점. 호출부는 {@link com.thinkfree.drive.auditlog.support.SettingAuditSupport}를
 * 직접 부르지 않고 이 서비스만 의존한다(Support는 내부 구현).
 *
 * <p>변경 전 값은 뮤테이션 전에 조회해야 하므로 호출부가 {@link com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService}
 * 등으로 미리 캡처해서 before로 넘긴다. 각 메서드는 before/after가 같으면 기록하지 않는다.
 */
public interface SettingAuditService {

    /** 테넌트 로그인 방식 변경(SETTING_UPDATE, objectName="loginMethod"). */
    void recordLoginMethodChangeIfNeeded(LoginType before, LoginType after);

    /** 테넌트 외부 공유 허용 여부 변경(SETTING_UPDATE, objectName="externalShare"). */
    void recordExternalShareChangeIfNeeded(boolean before, boolean after);

    /**
     * 슈퍼어드민 전역 로그인 정책 강제 적용 여부 변경. 영향받는 테넌트(SELF_MANAGED 타입 전체)마다
     * row를 fan-out한다(SETTING_UPDATE, objectName="globalLoginPolicy").
     */
    void recordGlobalLoginPolicyChangeIfNeeded(boolean oldEnforced, boolean newEnforced);

    /**
     * 슈퍼어드민 전역 공유 정책 강제 적용 여부 변경. 영향받는 테넌트(SELF_MANAGED 타입 전체)마다
     * row를 fan-out한다(SETTING_UPDATE, objectName="globalSharePolicy").
     */
    void recordGlobalSharePolicyChangeIfNeeded(boolean oldEnforced, boolean newEnforced);

    /** 허용 IP 목록 변경(SETTING_ALLOWED_IP_CHANGE). added/removed 델타를 계산해 변경분이 있을 때만 기록한다. */
    void recordAllowedIpChangeIfNeeded(List<String> beforeIps, List<String> afterIps);
}
