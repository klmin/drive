package com.thinkfree.drive.api.v1.user.response;

import com.thinkfree.drive.user.data.UserQuotaData;

public record UserQuotaResponse(
        Long totalQuotaByte,
        Long useQuotaByte,
        Boolean hasUnread
) {
    public static UserQuotaResponse from(UserQuotaData dto) {
        return new UserQuotaResponse(dto.getTotalQuotaByte(), dto.getUseQuotaByte(), false);
    }
}
