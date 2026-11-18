package com.thinkfree.drive.user.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserQuotaData {
    private Long totalQuotaByte;
    private Long useQuotaByte;
}
