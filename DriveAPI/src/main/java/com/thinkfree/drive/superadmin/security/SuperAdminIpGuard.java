package com.thinkfree.drive.superadmin.security;

import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.properties.SuperAdminIpProperty;
import com.thinkfree.drive.util.IpMatcherUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SuperAdminIpGuard {

    private final List<IpAddressMatcher> matchers;

    public SuperAdminIpGuard(SuperAdminIpProperty property) {
        log.info("property.allowedIps() : {}", property.allowedIps());
        this.matchers = IpMatcherUtil.build(property.allowedIps());
    }

    public void checkAllowed(String clientIp) {
        if (matchers.isEmpty()) {
            return;
        }
        if (!IpMatcherUtil.matchAny(matchers, clientIp)) {
            log.error("[SuperAdminIpGuard] blocked clientIp: {}", clientIp);
            throw ApiRuntimeException.type(AccessErrorCode.BLOCKED_IP);
        }
    }
}
