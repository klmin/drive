package com.thinkfree.drive.smtpconfig.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 암호화된 SMTP 비밀번호 값 객체 — {@code SmtpPasswordConverter} 로 컬럼 하나에 매핑된다.
 * 변경 감지는 이 타입의 {@code equals} 로 이전 값과 비교하므로 값 비교({@link EqualsAndHashCode})가 필요하다(없으면 새 인스턴스마다 항상 변경으로 판정).
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SmtpPassword {
    private String value;

    public static SmtpPassword of(String encryptedValue) {
        return new SmtpPassword(encryptedValue);
    }
}