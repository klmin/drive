package com.thinkfree.drive.user.quota.alert.state.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum QuotaThreshold {
    
    NONE(0),
    T80(80),
    T90(90),
    T100(100)
    ;
    
    private final Integer percent;

    public static QuotaThreshold fromPercent(int p) {
        if (p < T80.getPercent()){
            return NONE;
        }
        if (p < T90.getPercent()){
            return T80;
        }
        if (p < T100.getPercent()){
            return T90;
        }
        return T100;
    }
    public boolean isHigherThan(QuotaThreshold prev) {
        return this.percent > prev.getPercent();
    }
}
