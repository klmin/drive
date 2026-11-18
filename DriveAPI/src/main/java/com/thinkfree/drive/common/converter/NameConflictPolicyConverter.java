package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class NameConflictPolicyConverter implements Converter<String, NameConflictPolicy> {
    @Override
    public NameConflictPolicy convert(String source) {
        return NameConflictPolicy.from(source);
    }
}
