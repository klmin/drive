package com.thinkfree.drive.oauth2.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * AS가 인정하는 OAuth2 scope 전체 — DCR 검증·기본 부여, RS 메타데이터
 * (RFC 9728 scopes_supported) 광고, RS 필터의 권한 매핑까지의 단일 출처.
 * scope를 추가할 땐 여기에 상수만 추가하면 광고·검증에 함께 반영된다.
 *
 * 네이밍은 "리소스:행위" — mcp:*는 MCP RS(/mcp) 전용이라, 나중에 일반 API를
 * OAuth 토큰에 열면 api:* 계열을 추가해 리소스 단위로 크게 구분한다.
 *
 * SAS 동의(consent)는 scope 단위라서 scope 없는 인가 요청은 동의 화면이
 * 스킵되거나 빈 동의 폼이 될 수 있다. 이를 막기 위해
 * ① DCR이 scope 미지정 등록에 전체 scope를 기본 부여하고
 * ② RS 메타데이터가 광고해 클라이언트가 authorize 요청에 싣게 한다.
 *
 * value는 토큰 scope 클레임·동의 화면·consent 테이블·등록 클라이언트 행에
 * 그대로 저장되는 프로토콜 문자열 — 바꾸면 기존 등록·동의 기록과 어긋난다.
 * 프론트 동의 화면(DriveFront pages/oauth2-consent)의 표시명 매핑과도 계약.
 */
@Getter
@RequiredArgsConstructor
public enum OAuth2Scope {

    MCP_READ("mcp:read"),
    MCP_WRITE("mcp:write");

    /** Spring Security 관례 — scope는 SCOPE_ 프리픽스 authority로 매핑된다 */
    public static final String AUTHORITY_PREFIX = "SCOPE_";

    private final String value;

    private static final List<String> SUPPORTED_VALUES =
            Arrays.stream(values()).map(OAuth2Scope::getValue).toList();

    /** SecurityContext 권한 검사용 표기 (예: SCOPE_mcp:read) */
    public String authority() {
        return AUTHORITY_PREFIX + value;
    }

    /** 지원 scope의 프로토콜 문자열 목록 — scopes_supported 광고, DCR 기본 부여용 */
    public static List<String> supportedValues() {
        return SUPPORTED_VALUES;
    }

    public static boolean isSupported(String value) {
        return SUPPORTED_VALUES.contains(value);
    }
}
