package com.thinkfree.drive.api.v1.superadmin.apikey.request;

import com.thinkfree.drive.user.enums.RoleType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 슈퍼관리자 발급 요청.
 * 슈퍼관리자는 소속 테넌트가 없어 키가 속할 테넌트 지정 필수 — 본인 키도 해당 테넌트 호스트로 들어와야 인증됨.
 */
public record SuperAdminApiKeyCreateRequest(
        /* 슈퍼관리자 대상이면 비워야 하고, 관리자·사용자 대상이면 필수 */
        Integer tenantSeq,
        /* 미지정 시 본인. 지정 시 tenantSeq 에 속한 계정이어야 함 */
        Integer userSeq,
        @NotBlank
        @Size(max = 100)
        String keyName,
        @NotBlank
        @Size(max = 255)
        String keyPurpose,
        @NotNull
        @FutureOrPresent
        LocalDate expireDate,
        /* 허용 조합은 읽기 전용(api:read mcp:read) / 읽기+쓰기(4개) 두 가지. 대상 role 과 무관하게 필수 */
        @NotEmpty
        List<String> scopes,
        /* 키 역할 — 다른 계정에 발급할 때 대상 보유 역할 중 USER·ADMIN. 대상이 둘 다 보유하면 필수, 하나면 생략 가능(그 역할). 본인 발급이면 무시 */
        RoleType roleType,
        /* 보호 키 — true 면 만료일·활성화·비활성화·재발급을 슈퍼관리자 경로에서만 할 수 있다. 생략 시 false */
        Boolean pt
) {
}
