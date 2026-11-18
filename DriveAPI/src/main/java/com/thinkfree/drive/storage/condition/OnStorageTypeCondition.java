package com.thinkfree.drive.storage.condition;

import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;

/** {@link ConditionalOnStorageType} 구현. */
public class OnStorageTypeCondition implements Condition {

    private static final String PROPERTY = "storage.type";
    private static final StorageType DEFAULT = StorageType.CEPH;

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnStorageType.class.getName());
        if (attrs == null) {
            return false;
        }
        StorageType[] allowed = (StorageType[]) attrs.get("value");
        StorageType current = resolve(context.getEnvironment().getProperty(PROPERTY));
        return Arrays.asList(allowed).contains(current);
    }

    private static StorageType resolve(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT;
        }
        try {
            return StorageType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    PROPERTY + " 값이 올바르지 않습니다: '" + raw + "'. 허용값: " + Arrays.toString(StorageType.values()), e);
        }
    }
}
