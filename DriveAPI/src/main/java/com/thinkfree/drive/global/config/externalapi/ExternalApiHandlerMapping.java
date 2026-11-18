package com.thinkfree.drive.global.config.externalapi;

import com.thinkfree.drive.constants.DriveConstants;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

public class ExternalApiHandlerMapping extends RequestMappingHandlerMapping {

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        
        super.registerHandlerMethod(handler, method, mapping);

        if (!method.isAnnotationPresent(ExternalApi.class)) {
            return;
        }

        var pathPatternsCondition = mapping.getPathPatternsCondition();
        
        if (pathPatternsCondition == null) {
            return;
        }
        
        ExternalApi annotation = method.getAnnotation(ExternalApi.class);
        String[] externalPaths;

        if (!annotation.path().isEmpty()) {
            externalPaths = new String[]{DriveConstants.API_PUBLIC_V1_PREFIX + annotation.path()};
        } else {
            externalPaths = pathPatternsCondition.getPatterns().stream()
                    .filter(p -> p.getPatternString().startsWith(DriveConstants.API_V1_PREFIX))
                    .map(p -> p.getPatternString().replaceFirst(
                            "^" + DriveConstants.API_V1_PREFIX,
                            DriveConstants.API_PUBLIC_V1_PREFIX))
                    .toArray(String[]::new);
        }

        if (externalPaths.length == 0) {
            return;
        }
        
        RequestMappingInfo externalMapping = mapping.mutate()
                .paths(externalPaths)
                .options(getBuilderConfiguration())
                .build();

        super.registerHandlerMethod(handler, method, externalMapping);
    }
}
