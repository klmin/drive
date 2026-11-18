package com.thinkfree.drive.mcp.support;

import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/**
 * MCP 툴 로케일 어스펙트 — 사용자에게 표시되는 문자열(copy 사본 접미사, read/download reason,
 * offset 검증 메시지 등 메시지 번들 산출물)이 사용자의 언어 설정(tb_user.user_language)을 따르도록
 * 툴 실행 동안 LocaleContextHolder를 세팅하고 종료 시 복원한다.
 *
 * 웹은 브라우저의 Accept-Language를 그대로 쓰지만(기본 LocaleResolver — 웹 동작 무변경),
 * MCP 클라이언트는 보통 언어를 보내지 않아 서버 JVM 로케일로 떨어진다 — 환경에 따라 결과가
 * 달라지므로 userLanguage 기반으로 고정한다(채널별 로케일).
 *
 * 지점이 필터·인터셉터가 아니라 @Tool 어스펙트인 이유: 요청 스레드에서 세팅한 로케일이 툴까지
 * 전달되지 않는 경우가 실측됐고(Accept-Language 지정 실험 미동작 — 스레드 단절 추정),
 * SecurityContext는 툴 실행 지점 전파가 whoami로 검증돼 있어 같은 지점에서 읽는 것이 안전하다.
 * 사용자 조회는 툴 호출당 1회 — 인증 필터의 사용자 검증 조회와 같은 수준의 비용이다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class McpToolLocaleAspect {

    private final UserQueryRepository userQueryRepository;

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        Locale locale = resolveUserLocale();
        if (locale == null) {
            return joinPoint.proceed();
        }

        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(locale);
        try {
            return joinPoint.proceed();
        } finally {
            LocaleContextHolder.setLocaleContext(previous); // previous가 null이면 reset과 동일
        }
    }

    /**
     * KO/EN/ES는 그대로 매핑. AUTO(클라이언트 따름)·미설정은 요청이 Accept-Language를 명시한 경우만
     * 기존 로케일을 유지(null 반환)하고, 아니면 서버 JVM 로케일 대신 영어로 고정한다
     * (MessageSource 기본 로케일과 정합 — 실행 환경 비의존).
     */
    private Locale resolveUserLocale() {
        var details = SecurityUtil.getUserDetails();
        if (details == null || details.getUserSeq() == null) {
            return null; // 미인증 — 시큐리티 체인이 401로 차단하는 경로, 로케일은 기본 유지
        }

        UserDetailData user = userQueryRepository.findActiveDetailByUserSeq(details.getUserSeq());
        UserLanguage language = user == null ? null : user.getUserLanguage();
        if (language != null && language != UserLanguage.AUTO) {
            return Locale.forLanguageTag(language.getLanguage());
        }
        return hasAcceptLanguage() ? null : Locale.ENGLISH;
    }

    private boolean hasAcceptLanguage() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null;
    }
}
