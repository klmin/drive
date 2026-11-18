package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.common.validation.DomainValidator;
import com.thinkfree.drive.tenant.data.TenantData;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 테넌트 주소 체계 전략 — 요청에서 테넌트를 찍어내는 인바운드와, 테넌트별 URL origin 을 만드는 아웃바운드를 한 쌍으로 묶는다.
 * {@code tenant.resolution-mode} 에 따라 구현 빈 하나만 로딩된다 ({@code @ConditionalOnProperty}).
 * <ul>
 *     <li>{@link SubdomainTenantAddressing} — {@code acme.drive.example.com}</li>
 *     <li>{@link PathTenantAddressing} — {@code drive.example.com/t/acme/...}</li>
 * </ul>
 * 공통 규칙(시스템 공개 경로 스킵, INACTIVE·NOT_FOUND 404, 단일 테넌트/dev fallback, 404)은
 * {@link com.thinkfree.drive.global.filter.TenantFilter} 가 담당하고, 전략은 "주소 → 테넌트 특정" 과 "origin 조립" 만 맡는다.
 */
public interface TenantAddressingStrategy {

    /**
     * 요청 주소(Host 또는 경로 마커)로 테넌트를 특정한다.
     * 존재+ACTIVE → MATCHED(+체인에 넘길 요청), 존재+비활성 → INACTIVE, 마커 문법 불량 → NOT_FOUND,
     * 특정 불가(미등록 포함) → NONE(+fallback 시 체인에 넘길 요청, 선택).
     */
    TenantMatch resolve(HttpServletRequest request);

    /**
     * 테넌트의 아웃바운드 origin. 예) {@code https://acme.drive.example.com} / {@code https://drive.example.com/t/acme}
     *
     * 호스트가 베이스 도메인에서 오면 베이스에 입력한 스킴·포트가 아래 두 인자보다 우선한다({@link OriginComposer}).
     *
     * @param scheme 요청 스킴(요청이 없으면 https)
     * @param port   null 이면 생략(표준 포트)
     */
    String origin(TenantData tenant, String scheme, Integer port);

    /**
     * 슈퍼어드민이 등록하는 베이스 도메인의 <b>호스트</b>로 받을 수 있는 값인지(스킴·포트는
     * {@link com.thinkfree.drive.basedomain.address.BaseDomainAddress} 가 모양만 검사하고 모드와 무관하게 허용).
     * 기본(SUBDOMAIN)은 도메인 또는 {@code localhost} — 앞에 서브도메인 라벨을 붙여 테넌트 호스트를 만들어야 하므로 IP 는 불가
     * ({@code acme.192.168.0.10} 은 DNS 로 풀리지 않는다). PATH 모드는 베이스 도메인이 곧 공개 호스트라
     * IPv4 도 허용한다({@link PathTenantAddressing}).
     */
    default boolean isAcceptableBaseDomain(String host) {
        return DomainValidator.isValid(host) || DomainValidator.isLocalhost(host);
    }

    /**
     * 테넌트 등록·수정·재바인딩 시 저장되는 합성 도메인({@code subdomain.baseDomain}, 루트 테넌트는 base 자체 — 포트 없는 호스트)으로 받을 수 있는 값인지.
     * 기본(SUBDOMAIN)은 이 값이 인바운드 Host 판별 키라 도메인({@code acme.localhost} 포함) 또는 루트 테넌트의 {@code localhost}.
     * PATH 모드는 판별·아웃바운드에 쓰지 않는 저장값이라 IPv4 베이스 위의 합성값({@code acme.192.168.0.10})과
     * 순수 IPv4(루트 테넌트)도 허용한다({@link PathTenantAddressing}).
     */
    default boolean isAcceptableTenantDomain(String domain) {
        return DomainValidator.isValid(domain) || DomainValidator.isLocalhost(domain);
    }
}
