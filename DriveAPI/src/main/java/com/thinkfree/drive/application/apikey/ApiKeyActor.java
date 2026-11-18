package com.thinkfree.drive.application.apikey;

import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.user.enums.RoleType;

/**
 * 발급을 요청한 로그인 사용자. 발급 검증(권한 상한·대상·테넌트)의 기준이 된다.
 *
 * <p>Command 가 아니라 별도 파라미터로 받는다 — 요청 본문에 섞이면 클라이언트가 보낸 값처럼 보인다.
 *
 * <p>키의 테넌트는 슈퍼관리자를 뺀 나머지가 이 값을 쓴다. {@code TenantContext}(Host 기준)가 아닌 이유는
 * {@code ApiKeyIssuer} javadoc 참고.
 */
public record ApiKeyActor(int userSeq, RoleType roleType, Integer tenantSeq) {

    /** 내부 API·외부 API·MCP 모두 CustomUserDetails 를 principal 로 쓰므로 진입점과 무관하게 재사용된다 */
    public static ApiKeyActor of(CustomUserDetails loginUser) {
        return new ApiKeyActor(loginUser.getUserSeq(),
                RoleType.fromAuthority(loginUser.getRoleType()),
                loginUser.getTenantSeq());
    }
}
