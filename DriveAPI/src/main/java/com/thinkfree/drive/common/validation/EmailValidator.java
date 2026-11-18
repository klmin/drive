package com.thinkfree.drive.common.validation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailValidator {
    
    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9_+&*-]+(?:\\." +
            "[a-zA-Z0-9_+&*-]+)*@" +
            "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
            "A-Z]{2,7}$";
    
    private static final Pattern EMAIL_MATCHER = Pattern.compile(EMAIL_PATTERN);
    
    public static void validateOrThrow(String value) {
        if (!isValid(value)) {
            throw ApiRuntimeException.type(UserErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    public static boolean isValid(String email) {
        return EMAIL_MATCHER.matcher(email).matches();
    }
}
