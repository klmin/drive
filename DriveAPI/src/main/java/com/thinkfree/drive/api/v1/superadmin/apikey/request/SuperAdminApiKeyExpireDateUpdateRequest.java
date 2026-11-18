package com.thinkfree.drive.api.v1.superadmin.apikey.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 슈퍼관리자 만료일 수정 요청.
 * 만료된 키도 미래 날짜로 연장하면 다시 사용할 수 있다 — 만료 판정이 조회 시점 NOW() 비교라 즉시 복구된다.
 */
public record SuperAdminApiKeyExpireDateUpdateRequest(
        /* 무기한 불허. 날짜만 받아 그 날 23:59:59 에 만료 — 오늘 날짜까지 허용 */
        @NotNull
        @FutureOrPresent
        LocalDate expireDate
) {
}
