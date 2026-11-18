package com.thinkfree.drive.tenant.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 가 테넌트 정책으로 거부될 수 있음을 문서에 알린다 — 판정은 하지 않는다(판정은 {@link TenantFeatureRequired}·{@link TenantPolicy}).
 * 컨트롤러 메서드에 {@link TenantFeatureRequired} 가 붙어 있으면 그 기능은 문서에 자동으로 실리므로 이 선언이 필요 없다.
 * 판정이 UseCase·서비스에 있을 때(대부분) 컨트롤러에 이 선언을 단다.
 * OpenAPI 문서의 {@code x-drive.restrictedWhen} 으로 나가며, 조건은 {@link DefaultTenantPolicy} 규칙 표에서 만든다.
 * 문서를 읽는 쪽(Developers 플레이그라운드 등)은 자기 테넌트 유형·모드·보호 계정 여부가 조건 중 하나에 맞으면 실행을 막는다.
 * <p>
 * 요청 본문에 따라서만 막히는 경우(일괄 등록의 신규 항목, CSV 등록의 안내 메일 등)도 넣는다 — "막힐 수 있음"을 뜻한다.
 * <p>
 * 마스킹 기능({@code *_INFO_VIEW}, {@link DefaultTenantPolicy#isMasked})은 거부가 아니라 조회 응답 값을 가리므로
 * {@code x-drive.maskedWhen} 으로 따로 나간다 — 받는 쪽은 표시만 하고 실행은 막지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantRestriction {

    /** 이 API 가 판정받는 기능 */
    TenantFeature[] value() default {};

    /** 이 API 가 받는 보호 계정 판정 */
    ProtectedAccountRule[] protectedAccount() default {};
}
