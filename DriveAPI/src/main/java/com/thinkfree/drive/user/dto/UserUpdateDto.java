package com.thinkfree.drive.user.dto;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.UserLanguage;
import org.springframework.web.multipart.MultipartFile;

/**
 * 본인 정보 수정 service 입력 — UserService.updateCurrentUser.
 * 현재 프로필 이미지 키는 service 가 DB 값으로 쓰므로 받지 않는다(요청의 sk 무시).
 */
public record UserUpdateDto
(
    String phoneNumber,
    UserLanguage userLanguage,
    MultipartFile profileImageFile,
    DefaultLandingPage defaultLandingPage,
    String countryCode,
    boolean deleteProfileImage
)
{
}
