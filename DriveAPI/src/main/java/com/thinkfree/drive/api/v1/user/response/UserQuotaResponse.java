package com.thinkfree.drive.api.v1.user.response;

import com.thinkfree.drive.user.data.UserQuotaData;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param uploadingByte 진행 중(PENDING·COMPLETING) 업로드 세션의 신고 크기 합계. {@code useQuotaByte} 에 포함되지 않는 별도 값 —
 *                      화면이 "사용 중 + 업로드 중" 두 구간으로 보여 주는 용도(설계 규칙 12). 알림·지표 판단에는 쓰지 않는다.
 */
public record UserQuotaResponse(
        Long totalQuotaByte,
        Long useQuotaByte,
        @Schema(description = "Bytes reserved by uploads still in progress. Not included in useQuotaByte; committed only when each upload completes.")
        Long uploadingByte,
        Boolean hasUnread
) {
    public static UserQuotaResponse of(UserQuotaData dto, long uploadingByte) {
        return new UserQuotaResponse(dto.getTotalQuotaByte(), dto.getUseQuotaByte(), uploadingByte, false);
    }
}
