package com.thinkfree.drive.smtpconfig.auth;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;

public class OAuth2Authenticator extends Authenticator {

    private final String userEmail;
    private final String accessToken;

    public OAuth2Authenticator(String userEmail, String accessToken) {
        this.userEmail = userEmail;
        this.accessToken = accessToken;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        // OAUTH는 비밀번호 자리에 토큰을 그대로 사용
        return new PasswordAuthentication(userEmail, accessToken);
    }
}

