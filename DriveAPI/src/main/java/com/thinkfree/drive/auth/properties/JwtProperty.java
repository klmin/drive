package com.thinkfree.drive.auth.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 로그인 access·refresh 토큰 서명 키와 만료 시간.
 *
 * @param secret     HS256 서명 키
 * @param accessExp  access 토큰·쿠키 만료
 * @param refreshExp refresh 토큰·쿠키 만료
 */
@Validated
@ConfigurationProperties("jwt")
public record JwtProperty
(
        @Valid @NotNull Secret secret,
        @NotNull Duration accessExp,
        @NotNull Duration refreshExp
)
{
    /**
     * @param key 환경변수 DRIVE_JWT_SECRET_KEY 로 주입. 주입이 빠지면 바인딩이 "${...}" 글자 그대로 남으므로 그 경우를 막는다
     */
    public record Secret(
            @NotBlank
            @Pattern(regexp = "^(?!\\$\\{).*", message = "jwt.secret.key 가 필요합니다 — 환경변수 DRIVE_JWT_SECRET_KEY 를 주입하세요")
            String key
    ) {
    }
}
