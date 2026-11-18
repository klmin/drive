package com.thinkfree.drive.storage.condition;

import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code storage.type}이 지정한 종류 중 하나일 때만 빈을 등록한다.
 * <p>
 * 접속 프로토콜을 공유하는 종류는 배열로 묶고(예: S3 프로토콜 계층은 {@code {CEPH, S3, RUSTFS}}),
 * 종류마다 갈리는 구현(용량 조회 등)은 하나씩 지정한다.
 * {@code storage.type}이 없으면 {@link StorageType#CEPH}로 간주한다(기존 matchIfMissing 동작 유지).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnStorageTypeCondition.class)
public @interface ConditionalOnStorageType {

    StorageType[] value();
}
