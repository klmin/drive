package com.thinkfree.drive.oauth2.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 익명 DCR(/connect/register) 남용 방지 정책 (application.yml `oauth2.registration.*`).
 *
 * @param perIpLimit    한 클라이언트 IP 가 window 안에 등록할 수 있는 수 — WAS 인스턴스별 메모리 카운터라 인스턴스 수만큼 늘어난다
 * @param globalLimit   전체 등록 수 상한(window 안) — DB 기준이라 인스턴스 수와 무관. IP 를 바꿔 가며 등록해도 여기서 막힌다
 * @param window        두 상한의 집계 구간
 * @param unusedTtl     인가·연결 이력이 한 번도 없는 등록 클라이언트를 정리 스케줄러가 지우기까지의 유예
 */
@ConfigurationProperties("oauth2.registration")
public record OAuth2RegistrationProperty(
        @DefaultValue("10") int perIpLimit,
        @DefaultValue("500") int globalLimit,
        @DefaultValue("1h") Duration window,
        @DefaultValue("7d") Duration unusedTtl
) {
}
