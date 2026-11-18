package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MapStructUtil {
    
    public static int resolveUserSeq(Integer userSeq, int requestUserSeq) {
        return userSeq == null ? requestUserSeq : userSeq;
    }
}
