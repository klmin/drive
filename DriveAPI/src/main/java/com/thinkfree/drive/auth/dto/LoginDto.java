package com.thinkfree.drive.auth.dto;

/** 아이디·비밀번호 로그인 service 입력 — AuthService.login·adminLogin·superAdminLogin */
public record LoginDto
(
    String userId,
    String encPwd
)
{
}
