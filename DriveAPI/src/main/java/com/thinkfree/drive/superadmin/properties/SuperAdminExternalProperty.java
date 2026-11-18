package com.thinkfree.drive.superadmin.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * SUPER_ADMIN 권한의 외부 호출용 설정.
 * <p>
 * {@code static-api-key} 는 발급 절차 없이 외부 호출에 바로 쓰는 고정 API Key 로,
 * profile별 yml에서 인스톨러가 값을 주입한다. 미설정(빈 값)이면 비활성이다.
 * <p>
 * yml: {@code drive.super-admin.external.static-api-key: ${DRIVE_STATIC_API_KEY:}}
 */
@ConfigurationProperties("drive.super-admin.external")
public record SuperAdminExternalProperty
(
        String staticApiKey
)
{

    /** 고정 키가 설정되어 있는지 — 빈 값/미설정이면 false(기능 비활성). */
    public boolean hasStaticApiKey() {
        return StringUtils.hasText(staticApiKey);
    }

    /** 들어온 키가 설정된 고정 키와 일치하는지. 미설정이면 항상 false. */
    public boolean matches(String candidate) {
        return hasStaticApiKey() && staticApiKey.equals(candidate);
    }
}
