package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.util.LogSafe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 외부 경로의 API Key scope 검사. {@link ApiScopeRegistry} 가 모은 요구 scope 와 키의 scope 를 대조한다.
 * <p>
 * 요구 scope 선언이 없으면 통과시킨다 — 읽기는 체인 게이트({@code api:*} 하나라도)가 겸하고,
 * 그 밖의 개방 endpoint 대부분은 애초에 검사 대상이 아니기 때문이다.
 * 발급 허용 조합상 쓰기 키는 읽기를 항상 포함하므로, WRITE 요구는 {@code api:write} 만 보면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiScopeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * scope 미보유로 거부했음을 {@link ApiKeyAccessDeniedHandler} 에 알리는 request attribute.
     * {@code AuthorizationManager} 는 거부 사유를 예외로 전달할 수 없어 attribute 로 넘긴다.
     */
    public static final String SCOPE_DENIED_ATTRIBUTE = ApiScopeAuthorizationManager.class.getName() + ".scopeDenied";

    private final ApiScopeRegistry apiScopeRegistry;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        ApiKeyScope required = apiScopeRegistry.requiredScope(context.getRequest());

        if (required == null) {
            return new AuthorizationDecision(true);
        }

        Authentication auth = authentication.get();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return new AuthorizationDecision(false);
        }

        if (details.hasScope(required)) {
            return new AuthorizationDecision(true);
        }

        context.getRequest().setAttribute(SCOPE_DENIED_ATTRIBUTE, required.getValue());
        log.info("{ ApiScopeAuthorizationManager } : scope 미보유 - required : {}, uri : {}",
                required.getValue(), LogSafe.of(context.getRequest().getRequestURI()));
        return new AuthorizationDecision(false);
    }
}
