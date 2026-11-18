package com.thinkfree.drive.sso.registry;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.handler.SsoAuthHandler;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SsoHandlerRegistry {

    private final Map<SsoProviderProtocolType, SsoAuthHandler> handlers = new EnumMap<>(SsoProviderProtocolType.class);

    public SsoHandlerRegistry(List<SsoAuthHandler> handlerList) {
        for (var handler : handlerList) {
            var key = handler.supports();
            var prev = handlers.putIfAbsent(key, handler);
            if (prev != null) {
                throw new IllegalStateException("Duplicate handler for " + key);
            }
        }
    }

    public SsoAuthHandler get(SsoProviderProtocolType type) {
        SsoAuthHandler handler = handlers.get(type);
        if (handler == null) {
            throw ApiRuntimeException.type(SsoErrorCode.UNSUPPORTED_PROTOCOL).with("protocolType", type);
        }
        return handler;
    }
}
