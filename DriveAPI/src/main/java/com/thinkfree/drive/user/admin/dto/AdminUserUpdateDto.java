package com.thinkfree.drive.user.admin.dto;

import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자의 사용자 정보 수정 service 입력 — UserService.updateUserByAdmin.
 * 정규화/기본값은 application 커맨드가 이미 끝낸 값을 받는다.
 * 프로필 이미지 처리 후 service 가 로드한 관리 엔티티를 User#changeByAdmin 으로 고친다(변경 감지, W7).
 */
public record AdminUserUpdateDto
(
    String userName,
    Long totalQuotaByte,
    String countryCode,
    String phoneNumber,
    MultipartFile profileImageFile,
    Boolean deleteProfileImage
)
{
}
