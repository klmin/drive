package com.thinkfree.drive.license.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 라이선스 검사 스위치 — 라이선스 파일 없이 로컬에서 개발할 때만 끈다.
 *
 * <p>끄면 {@code GET /api/v1/config/license-status} 가 등록 여부와 상관없이 "사용 가능"을 돌려준다. 사용자 로그인 차단은
 * 프론트가 이 응답만 보고 하므로(백엔드는 라이선스로 로그인·요청을 막지 않음) 로그인이 풀린다. 관리자 화면의 미등록 배너,
 * 좌석 검사(미등록이면 원래 통과), 화이트라벨(미등록이면 꺼짐)은 그대로다.
 *
 * @param enforcementEnabled 라이선스 검사. 기본 true, <b>운영은 반드시 true</b>. false 면 기동 시 WARN 로그를 남긴다
 */
@ConfigurationProperties("drive.license")
public record LicenseEnforcementProperty
(
        @DefaultValue("true") boolean enforcementEnabled
)
{
}
