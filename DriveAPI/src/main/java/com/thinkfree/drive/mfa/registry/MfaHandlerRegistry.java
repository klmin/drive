package com.thinkfree.drive.mfa.registry;

import com.thinkfree.drive.mfa.handler.MfaHandler;
import com.thinkfree.drive.user.enums.MfaMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MfaHandlerRegistry {

    private final Map<MfaMethod, MfaHandler> handlers = new EnumMap<>(MfaMethod.class);

    public MfaHandlerRegistry(List<MfaHandler> handlerList) {
        for (MfaHandler handler : handlerList) {
            MfaMethod method = handler.supports();
            if (handlers.put(method, handler) != null) {
                throw new IllegalStateException("Duplicate MfaHandler for method : " + method);
            }
        }
    }

    public MfaHandler get(MfaMethod method) {
        MfaHandler handler = handlers.get(method);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported MFA method : " + method);
        }
        return handler;
    }
}
