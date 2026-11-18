package com.thinkfree.drive.tenant.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TenantType {
    /** 고객사가 관리자 화면으로 직접 운영하는 테넌트(기존 ORGANIZATION). 사용자 모드는 MULTI 고정 */
    SELF_MANAGED,
    /** 외부 시스템(플레이그라운드 서버 등)이 슈퍼관리자 API 로 프로비저닝·삭제하는 테넌트. 유형·모드 조합 규칙은 이 유형에만 적용 */
    SYSTEM_MANAGED
    ;
}
