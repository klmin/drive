package com.thinkfree.drive.sso.registry;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.service.SsoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SsoServiceRegistry {

    private final Map<SsoProviderProtocolType, SsoService> services = new EnumMap<>(SsoProviderProtocolType.class);

    public SsoServiceRegistry(List<SsoService> serviceList) {
        for (SsoService service : serviceList) {
            SsoProviderProtocolType key = service.supports();
            SsoService prev = services.putIfAbsent(key, service);
            if (prev != null) {
                throw new IllegalStateException("Duplicate SsoService for " + key);
            }
        }
    }

    public SsoService get(SsoProviderProtocolType type) {
        SsoService service = services.get(type);
        if (service == null) {
            throw ApiRuntimeException.type(SsoErrorCode.UNSUPPORTED_PROTOCOL).with("protocolType", type);
        }
        return service;
    }
}
