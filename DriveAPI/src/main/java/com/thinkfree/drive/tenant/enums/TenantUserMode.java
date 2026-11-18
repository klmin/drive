package com.thinkfree.drive.tenant.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** tb_tenant.user_mode — 생성 후 변경 불가. 차단 규칙은 {@link TenantType#SYSTEM_MANAGED} 일 때만 적용 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TenantUserMode {
    /** 생성 시 만든 보호 계정 하나만 — 사용자 등록 거부, API Key 발급·변경은 슈퍼관리자만 */
    SINGLE,
    /** 여러 계정(라이선스 좌석 안). SELF_MANAGED 는 항상 MULTI */
    MULTI
    ;
}
