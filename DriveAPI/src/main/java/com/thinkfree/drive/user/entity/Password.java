package com.thinkfree.drive.user.entity;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

/** 컨버터(PasswordConverter)로 매핑한 값 클래스 — Hibernate 변경 감지가 이 equals 로 판정한다(없으면 로드한 인스턴스가 항상 변경으로 보임, 핸드오프 §0-1 I) */
@EqualsAndHashCode
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {
    // 대문자, 소문자, 숫자, 특수문자(ASCII 기호 전체) 각각 1자 이상 포함, 최소 8자 최대 64자
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!\"#$%&'()*+,-./:;<=>?@[\\\\]^_`{|}~]).{8,64}$";
    private static final Pattern PASSWORD_MATCHER = Pattern.compile(PASSWORD_PATTERN);

    private String value;

    private Password(String value) {
        this.value = value;
    }

    public static Password encrypt(String value, PasswordEncoder encoder) {
        validatePasswordPattern(value);
        return new Password(encoder.encode(value));
    }
    
    public static Password encodeWithoutValidation(String value, PasswordEncoder encoder) {
        return new Password(encoder.encode(value));
    }

    public static void validatePasswordPattern(String value) {
        if (isNotValidPattern(value)) {
            throw ApiRuntimeException.type(UserErrorCode.INVALID_PASSWORD_PATTERN);
        }
    }

    public static boolean isNotValidPattern(String password) {
        return !PASSWORD_MATCHER.matcher(password).matches();
    }

    public boolean isSamePassword(String comparePassword, PasswordEncoder encoder) {
        return encoder.matches(comparePassword, this.value);
    }

    public static Password of(String encPwd) {
        return new Password(encPwd);
    }

}
