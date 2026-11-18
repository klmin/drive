package com.thinkfree.drive.api.v1.user.apikey.request;

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
 * 사용자 자가 발급 요청.
 * 대상 계정은 로그인 사용자 본인으로 고정 — 임의 계정 지정 허용 시 해당 계정 자격의 키 발급으로 계정 탈취 가능.
 */
public record UserApiKeyCreateRequest(
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
        /* 허용 조합은 읽기 전용(api:read mcp:read) / 읽기+쓰기(4개) 두 가지 */
        @ArraySchema(arraySchema = @Schema(title = "API permissions", description = "Permission scopes for this key. Send exactly one of two sets: read-only [api:read, mcp:read] or read-write [api:read, api:write, mcp:read, mcp:write]. Other combinations are rejected."), schema = @Schema(title = "Permission scope", description = "api:read, api:write, mcp:read or mcp:write."))
        @NotEmpty
        List<String> scopes
) {
}
