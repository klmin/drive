package com.thinkfree.drive.sso.protocol.registry;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.exception.SsoProtocolErrorCode;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolFindService;
import com.thinkfree.drive.sso.protocol.service.SsoProtocolService;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SsoProtocolRegistry {
    
    private final Map<SsoProviderProtocolType, SsoProtocolService> services = new EnumMap<>(SsoProviderProtocolType.class);
    private final Map<SsoProviderProtocolType, SsoProtocolFindService> findServices = new EnumMap<>(SsoProviderProtocolType.class);

    public SsoProtocolRegistry(List<SsoProtocolService> serviceList, List<SsoProtocolFindService> findServiceList) {
        
        for (var service : serviceList) {
            var key = service.supports();
            var prev = services.putIfAbsent(key, service);
            if (prev != null) {
                throw new IllegalStateException("Duplicate Service for " + key);
            }
        }
        
        for(var findService : findServiceList) {
            var key = findService.supports();
            var prev = findServices.putIfAbsent(key, findService);
            if (prev != null) {
                throw new IllegalStateException("Duplicate FindService for " + key);
            }
        }
    }
    
    public SsoProtocolService get(SsoProviderProtocolType type) {
        SsoProtocolService service = services.get(type);
        if (service == null) {
            throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL).with("protocolType", type);
        }
        return service;
    }
    
    public SsoProtocolFindService getFind(SsoProviderProtocolType type) {
        SsoProtocolFindService service = findServices.get(type);
        if (service == null) {
            throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL).with("protocolType", type);
        }
        return service;
    }
}
