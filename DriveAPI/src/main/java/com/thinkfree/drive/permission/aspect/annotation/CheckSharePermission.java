package com.thinkfree.drive.permission.aspect.annotation;

import com.thinkfree.drive.permission.enums.SharePermissionType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckSharePermission {
    SharePermissionType value() default SharePermissionType.SHARE;
    String sharedByUserSeq() default "";
    String shareSeq() default "";
    boolean isCheckShareSeq() default true;
}
