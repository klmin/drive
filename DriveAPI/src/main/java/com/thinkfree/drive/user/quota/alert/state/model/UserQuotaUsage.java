package com.thinkfree.drive.user.quota.alert.state.model;

import com.thinkfree.drive.user.quota.alert.state.enums.QuotaThreshold;
import com.thinkfree.drive.util.ObjectUtil;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class UserQuotaUsage {
    
    private static final int PERCENT_MIN = 0;
    private static final int PERCENT_MAX = 100;
    private static final double PERCENT_FACTOR = 100.0;
    
    private final long usedBytes;
    private final long limitBytes;
    
    public static UserQuotaUsage of(long usedBytes, long limitBytes){
        return UserQuotaUsage.builder()
                             .usedBytes(usedBytes)
                             .limitBytes(limitBytes)
                             .build();
    }

    public int percent() {
        if (limitBytes <= 0L) {
            return 0;
        }
        int percent = (int) Math.floor((usedBytes * PERCENT_FACTOR) / (double) limitBytes);
        return Math.max(PERCENT_MIN, Math.min(PERCENT_MAX, percent));
    }

    public QuotaThreshold threshold() {
        return QuotaThreshold.fromPercent(percent());
    }

    public boolean isHigherThan(QuotaThreshold prev) {
        return threshold().isHigherThan(prev);
    }
    
}
