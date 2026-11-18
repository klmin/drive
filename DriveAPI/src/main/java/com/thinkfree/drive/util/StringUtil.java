package com.thinkfree.drive.util;

import com.thinkfree.drive.constants.DriveConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtil {
    
    public static String nvl(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
    public static String toEmptyIfBlank(String value) {
        return nvl(value, DriveConstants.EMPTY_STRING);
    }
    
    public static String buildUrl(Object... paths) {
        StringBuilder sb = new StringBuilder();
        for (Object path : paths) {
            if(path == null){
                continue;
            }
            sb.append("/").append(path.toString().replaceFirst("^/+", ""));
        }
        return sb.toString();
    }
    
    public static String append(Object... objs){
        StringBuilder sb = new StringBuilder();
        for(Object path : objs){
            sb.append(path.toString());
        }
        return sb.toString();
    }
    
    public static void requireRange(String v, long min, long max, Supplier<? extends RuntimeException> ex) {
        int length = v.length();
        if (length < min || length > max) {
            throw ex.get();
        }
    }
    
    public static String normalizeCountryCode(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\+", "") : value;
    }

    public static String normalizePhoneNumber(String value) {
        return StringUtils.hasText(value) ? value.replace("-", "") : value;
    }

    public static void validateRequiredTogether(String a, String b, Supplier<? extends RuntimeException> ex) {
        boolean hasA = StringUtils.hasText(a);
        boolean hasB = StringUtils.hasText(b);
        if (hasA != hasB) {
            throw ex.get();
        }
    }

    public static String buildUrl(String baseUrl, String... paths) {
        if (baseUrl == null){
            return null;
        }
        
        StringBuilder url = new StringBuilder(baseUrl.replaceAll("/+$", ""));
    
        for (String path : paths) {
            if (!StringUtils.hasText(path)){
                continue;
            }
            url.append("/").append(path.replaceFirst("^/+", "").replaceAll("/+$", ""));
        }
    
        return url.toString();
    }
    
}
