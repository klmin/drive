package com.thinkfree.drive.permission.aspect.annotation;

import com.thinkfree.drive.permission.aspect.enums.DeletePermissionMode;
import com.thinkfree.drive.permission.enums.SharePermissionType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CheckResourcePermissions.class)
public @interface CheckResourcePermission {
    SharePermissionType value();
    String sharedByUserSeq() default "";
    String resourceSeq() default "";
    String shareSeq() default "";
    DeletePermissionMode isDelete() default DeletePermissionMode.NO_DELETE;
}
