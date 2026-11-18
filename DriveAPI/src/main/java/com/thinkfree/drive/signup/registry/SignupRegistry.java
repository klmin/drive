package com.thinkfree.drive.signup.registry;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.signup.exception.SignupErrorCode;
import com.thinkfree.drive.signup.service.SignupService;
import com.thinkfree.drive.user.enums.SignupType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SignupRegistry {
    
    private final Map<SignupType, SignupService> services = new EnumMap<>(SignupType.class);

    public SignupRegistry(List<SignupService> serviceList) {
        for (SignupService service : serviceList) {
            SignupType key = service.supports();
            SignupService prev = services.putIfAbsent(key, service);
            if (prev != null) {
                throw new IllegalStateException("Duplicate SignupService for " + key);
            }
        }
    }

    public SignupService get(SignupType type) {
        SignupService service = services.get(type);
        if (service == null) {
            throw ApiRuntimeException.type(SignupErrorCode.UNSUPPORTED_SERVICE).with("signupType", type);
        }
        return service;
    }
}
