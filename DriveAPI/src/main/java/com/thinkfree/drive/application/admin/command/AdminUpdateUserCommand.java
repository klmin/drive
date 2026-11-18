package com.thinkfree.drive.application.admin.command;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.util.StringUtil;
import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 관리자의 사용자 정보 수정 커맨드 — 웹 요청(AdminUserUpdateRequest)과 필드 동형 (축약키만 전체 이름).
 * 국가코드/전화번호 정규화·동반 필수 검증·deleteProfileImage 기본값(false)은 구 웹 request 소유였던 것을 커맨드가 가져왔다.
 * roles 는 교체 후 보유할 역할 전부 — null 이면 역할 유지.
 */
@Builder
public record AdminUpdateUserCommand
(
    String userName,
    Long totalQuotaByte,
    String countryCode,
    String phoneNumber,
    MultipartFile profileImageFile,
    Boolean deleteProfileImage,
    Set<RoleType> roles
)
{
    public AdminUpdateUserCommand {
        countryCode = StringUtil.normalizeCountryCode(countryCode);
        phoneNumber = StringUtil.normalizePhoneNumber(phoneNumber);
        deleteProfileImage = deleteProfileImage != null ? deleteProfileImage : Boolean.FALSE;
    }

    public void validatePhoneFields() {
        StringUtil.validateRequiredTogether(countryCode, phoneNumber,
                () -> ApiRuntimeException.type(UserErrorCode.PHONE_FIELDS_REQUIRED_TOGETHER));
    }

    /** 대상 사용자 역할로 확정한 할당량(TenantQuotaPolicy.resolveUserQuota)을 담은 커맨드 */
    public AdminUpdateUserCommand withTotalQuotaByte(long resolvedTotalQuotaByte) {
        return new AdminUpdateUserCommand(userName, resolvedTotalQuotaByte, countryCode, phoneNumber,
                profileImageFile, deleteProfileImage, roles);
    }
}
