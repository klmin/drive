package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DomainValidator {

    /**
     * 풀 도메인 검증.
     * - 라벨: [a-z0-9]로 시작/종료, 가운데 [a-z0-9-], 라벨당 최대 63자
     * - 라벨 2개 이상 (TLD 필수), TLD 는 영문 2~63자
     * 예: drive.com, www.drive.com, add.thinkfree.com, tenanta.company.co.kr
     */
    private static final String DOMAIN_PATTERN =
            "^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$";

    private static final Pattern DOMAIN_MATCHER = Pattern.compile(DOMAIN_PATTERN);

    /**
     * IPv4 리터럴 — 옥텟 0~255 네 개, 선행 0 불허({@code 01.2.3.4} 거부).
     * PATH 테넌트 모드의 베이스 도메인(공개 호스트)에만 허용되며, 서브도메인을 앞에 붙여야 하는 SUBDOMAIN 모드에서는 쓰지 않는다.
     * DNS 조회를 피하기 위해 {@code InetAddress} 대신 정규식으로 판별한다. IPv6 는 범위 밖.
     */
    private static final String IPV4_OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4_MATCHER = Pattern.compile("^(" + IPV4_OCTET + "\\.){3}" + IPV4_OCTET + "$");

    public static void validateOrThrow(String domain, ErrorCode errorCode) {
        if (!isValid(domain)) {
            throw ApiRuntimeException.type(errorCode);
        }
    }

    public static boolean isValid(String domain) {
        return StringUtils.hasText(domain) && DOMAIN_MATCHER.matcher(domain).matches();
    }

    /** 서브도메인 라벨(들) + IPv4 베이스 — PATH 모드에서 IPv4 베이스 도메인 위에 등록된 테넌트의 합성 domain({@code acme.192.168.0.10}). */
    private static final Pattern LABELS_ON_IPV4_MATCHER = Pattern.compile(
            "^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+(" + IPV4_OCTET + "\\.){3}" + IPV4_OCTET + "$");

    public static boolean isValidIpv4(String host) {
        return StringUtils.hasText(host) && IPV4_MATCHER.matcher(host).matches();
    }

    /** {@code label(.label)*.IPv4} — 라벨 규칙은 도메인과 동일, 끝이 IPv4. 순수 IPv4 는 false(라벨이 하나 이상 있어야 함). */
    public static boolean isValidLabelsOnIpv4(String host) {
        return StringUtils.hasText(host) && LABELS_ON_IPV4_MATCHER.matcher(host).matches();
    }

    /** 도메인 또는 IPv4 — 공개 호스트를 그대로 쓰는 곳(PATH 모드 베이스 도메인)용. */
    public static boolean isValidDomainOrIpv4(String host) {
        return isValid(host) || isValidIpv4(host);
    }

    /**
     * 단일 라벨 {@code localhost} — 루프백 예약 이름(RFC 6761 §6.3). TLD 가 없어 {@link #isValid} 는 거부하므로 따로 허용한다.
     * {@code acme.localhost} 처럼 라벨이 붙은 형태는 {@link #isValid} 가 이미 통과시킨다(브라우저가 {@code *.localhost} 를 루프백으로 해석).
     */
    public static boolean isLocalhost(String host) {
        return LOCALHOST.equals(host);
    }

    private static final String LOCALHOST = "localhost";
}
