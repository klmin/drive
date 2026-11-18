package com.thinkfree.drive.auditlog.aspect.annotation;

import com.thinkfree.drive.auditlog.enums.EventName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 어노테이션이 적용된 메서드 실행이 완료되면
 * {@link com.thinkfree.drive.auditlog.aspect.AuditLogAspect}가
 * {@link com.thinkfree.drive.auditlog.context.AuditLogContextHolder}의 이벤트를 조회하여
 * 공통 필드와 병합한 뒤 감사 로그를 전송한다.
 *
 * <p>
 * FILE_FOLDER, SHARE, USER_MANAGEMENT, SETTING 등 대부분의 이벤트는
 * usecase/service 계층에서 필요한 정보를 ThreadLocal에 적재하므로
 * {@code @AuditLog}만 선언하면 된다.
 *
 * <p>
 * LOGIN_SUCCESS, LOGOUT처럼 별도의 추가 수집 정보가 필요 없는 이벤트는
 * {@code eventName} 또는 {@code exceptionEventName}을 사용해
 * 공통 필드만으로 구성된 감사 로그를 직접 생성할 수 있다.
 *
 * <ul>
 *   <li>{@code eventName}: 메서드가 정상 종료된 경우 기록할 이벤트</li>
 *   <li>{@code exceptionEventName}: 예외 발생 시 기록할 이벤트</li>
 * </ul>
 *
 * <p>
 * 예:
 * <ul>
 *   <li>{@code AuthService.issueLoginTokensAndLog} → {@code eventName = LOGIN_SUCCESS}</li>
 *   <li>{@code AuthUseCase.login} → {@code exceptionEventName = LOGIN_FAILED}</li>
 * </ul>
 *
 * <p>
 * ThreadLocal 기반 이벤트와 {@code eventName}/{@code exceptionEventName}은 함께 사용할 수 있다.
 * 예를 들어 로그인 5회 실패로 계정이 잠기는 경우
 * ACCOUNT_LOCKED_STATUS_CHANGE(ThreadLocal)와
 * LOGIN_FAILED(exceptionEventName)가 각각 기록되어
 * 감사 로그가 2건 생성된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    EventName[] eventName() default {};

    EventName[] exceptionEventName() default {};
}
