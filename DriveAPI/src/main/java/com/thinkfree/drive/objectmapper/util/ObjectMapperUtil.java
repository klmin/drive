package com.thinkfree.drive.objectmapper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.objectmapper.exception.JsonProcessingRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObjectMapperUtil {
    private final ObjectMapper objectMapper;
    
    @Named("writeValueAsString")
    public String writeValueAsString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[writeValueAsString Exception]", e);
            throw new JsonProcessingRuntimeException("Failed to convert object to JSON string", e);
        }
    }
}
