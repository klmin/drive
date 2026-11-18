package com.thinkfree.drive.api.v1.admin.apikey.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 발급 요청.
 * 키의 권한은 요청으로 받지 않음 — 대상 계정의 실제 권한과 같아야 하므로 서버가 조회해 채움.
 */
public record AdminApiKeyCreateRequest(
        /* 미지정 시 본인. 지정 시 자기 테넌트에 속한 계정이어야 함 */
        @Schema(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.")
        Integer userSeq,
        @Schema(title = "API Key name", description = "Name used to identify this API Key in Drive.")
        @NotBlank
        @Size(max = 100)
        String keyName,
        @Schema(title = "API Key purpose", description = "Description of what this API Key will be used for.")
        @NotBlank
        @Size(max = 255)
        String keyPurpose,
        /* 무기한 불허. 날짜만 받아 그 날 23:59:59 에 만료 — 오늘 날짜까지 허용 */
        @Schema(title = "Expiration date", description = "Date on which this API Key expires, in YYYY-MM-DD format.")
        @NotNull
        @FutureOrPresent
        LocalDate expireDate,
        /* 허용 조합은 읽기 전용(api:read mcp:read) / 읽기+쓰기(4개) 두 가지. 대상 role 과 무관하게 필수 */
        @ArraySchema(arraySchema = @Schema(title = "API permissions", description = "Permission scopes for this key. Send exactly one of two sets: read-only [api:read, mcp:read] or read-write [api:read, api:write, mcp:read, mcp:write]. Other combinations are rejected."), schema = @Schema(title = "Permission scope", description = "api:read, api:write, mcp:read or mcp:write."))
        @NotEmpty
        List<String> scopes
) {
}
