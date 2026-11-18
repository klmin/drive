package com.thinkfree.drive.auditlog.lookup.tenant;

/**
 * tenantSeq → 테넌트명 해석. 감사 로그 전송 시 거버넌스 {@code scope.name}을 채우기 위해 사용한다.
 * <p>
 * 요청 컨텍스트(TenantContext)와 SYSTEM(스케줄러) 경로를 균일하게 커버하려면 seq만으로 이름을 얻어야 하므로
 * 이 조회로 통일한다. 이름은 seq당 1:1이고 거의 변하지 않아 구현체가 캐시한다.
 */
public interface TenantNameResolver {

    /** @return 테넌트명. seq가 null이거나 조회 실패 시 {@code null}(scope.name 생략). */
    String resolve(Integer tenantSeq);
}
