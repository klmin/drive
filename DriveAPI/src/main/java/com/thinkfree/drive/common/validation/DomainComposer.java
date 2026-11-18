package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 서브도메인 정규화 및 풀 도메인(subdomain + base) 조합/분해 유틸.
 * 풀 도메인은 항상 subdomain 과 base_domain 으로부터 서버가 재계산하는 denormalized 값이다.
 * <p>
 * 예약어 검증은 없다 — PATH 테넌트 모드 URL 이 {@code /t/{slug}} 마커 문법이라 슬러그가 루트 경로와 경쟁하지 않고,
 * SUBDOMAIN 모드는 Host 로 판별하므로 경로 예약어가 무의미하다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DomainComposer {

    // 서브도메인: 1개 이상의 라벨. 라벨당 [a-z0-9] 시작/종료, 가운데 [a-z0-9-], 최대 63자.
    private static final Pattern SUBDOMAIN_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$");

    /** 서브도메인(슬러그) 형식 검사(소문자 라벨). blank·형식 오류면 false. 예외 없이 판별만 필요할 때(경로 판별 등). */
    public static boolean isValidSubdomain(String subdomain) {
        return StringUtils.hasText(subdomain) && SUBDOMAIN_PATTERN.matcher(subdomain).matches();
    }

    /** 서브도메인 형식 검증(소문자 라벨). blank/형식오류 시 예외. */
    public static String normalizeSubdomain(String subdomain) {
        if (!isValidSubdomain(subdomain)) {
            throw ApiRuntimeException.type(TenantErrorCode.INVALID_TENANT_DOMAIN);
        }
        return subdomain;
    }

    /** subdomain + base 조합. subdomain 이 비어있으면 base 자체(루트 테넌트). */
    public static String compose(String subdomain, String baseDomain) {
        if (!StringUtils.hasText(subdomain)) {
            return baseDomain;
        }
        return subdomain + "." + baseDomain;
    }

    /**
     * 베이스 최초 등록 시, 기존 raw 풀 도메인에서 서브도메인(맨 앞 라벨)을 추출한다.
     * 점이 없거나(localhost 포함) IPv4 면 null(루트 테넌트로 간주) — IPv4 의 첫 옥텟({@code 192})은 서브도메인이 아니다.
     */
    public static String extractLeadingSubdomain(String fullDomain) {
        if (!StringUtils.hasText(fullDomain) || DomainValidator.isValidIpv4(fullDomain)) {
            return null;
        }
        int dot = fullDomain.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        return fullDomain.substring(0, dot).toLowerCase(Locale.ROOT);
    }
}
