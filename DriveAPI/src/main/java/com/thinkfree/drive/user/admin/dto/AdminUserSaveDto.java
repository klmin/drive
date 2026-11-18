package com.thinkfree.drive.user.admin.dto;

import com.thinkfree.drive.user.enums.RoleType;

import java.util.Set;

/**
 * 관리자의 사용자 등록 service 입력 — 단건(UserService.registerUser)·일괄(AdminUserService.registerAll/upsertAll) 공용.
 * 구 user.admin.command.AdminUserRegisterCommand 개명 (command는 application 계층 어휘 — service 계층은 dto).
 * encPwd는 원문(레거시 명명) — 인코딩은 비밀번호 변경류와 동일하게 service 내부에서 수행
 * (단건=Password.encrypt 패턴 검증 포함, 일괄=encodeWithoutValidation — CSV/bulk는 사전 검증 완료).
 * roles 는 부여할 역할 전부(tb_user_role).
 */
public record AdminUserSaveDto
(
        String userId,
        Integer tenantSeq,
        String encPwd,
        String userName,
        Long totalQuotaByte,
        Integer registerUserSeq,
        Set<RoleType> roles,
        String countryCode,
        String phoneNumber
)
{
}
