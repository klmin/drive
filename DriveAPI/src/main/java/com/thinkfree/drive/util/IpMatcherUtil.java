package com.thinkfree.drive.util;

import lombok.NoArgsConstructor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import java.util.List;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class IpMatcherUtil {

    public static List<IpAddressMatcher> build(List<String> cidrs) {
        if (cidrs == null) {
            return List.of();
        }
        return cidrs.stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    public static boolean matchAny(List<IpAddressMatcher> matchers, String clientIp) {
        if (matchers == null || matchers.isEmpty() || !StringUtils.hasText(clientIp)) {
            return false;
        }
        try {
            return matchers.stream().anyMatch(m -> m.matches(clientIp));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
