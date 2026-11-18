package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlUtil {
     public static String escape(String s) {
        if (s == null){
            return null;
        }
        return s.replace("%", "\\%")
                .replace("_", "\\_");
    }
}
