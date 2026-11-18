package com.thinkfree.drive.signup.service;

import com.thinkfree.drive.signup.dto.SignupDto;
import com.thinkfree.drive.signup.result.SignupResult;
import com.thinkfree.drive.user.enums.SignupType;
import jakarta.servlet.http.HttpServletRequest;

public interface SignupService {
    SignupType supports();
    SignupResult signup(SignupDto dto, HttpServletRequest httpServletRequest);
}
