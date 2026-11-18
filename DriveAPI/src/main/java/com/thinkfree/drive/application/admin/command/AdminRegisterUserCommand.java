package com.thinkfree.drive.application.admin.command;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.util.StringUtil;
import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 관리자의 단건 사용자 등록 커맨드 — 웹 요청(UserRegisterRequest)과 필드 동형 (축약키만 전체 이름).
 * 국가코드/전화번호 정규화와 동반 필수 검증은 구 웹 request 소유였던 것을 커맨드가 가져왔다.
 * encPwd는 원문 그대로 담고, 인코딩(Password.encrypt)은 비밀번호 변경류와 동일하게 UserService.registerUser 내부에서 수행한다.
 * roles 는 부여할 역할 전부(USER·ADMIN, 1개 이상 — usecase 가 검사).
 */
@Builder
public record AdminRegisterUserCommand
(
    String userId,
    String encPwd,
    String userName,
    Long totalQuotaByte,
    Set<RoleType> roles,
    MultipartFile profileImageFile,
    String countryCode,
    String phoneNumber
)
{
    public AdminRegisterUserCommand {
        countryCode = StringUtil.normalizeCountryCode(countryCode);
        phoneNumber = StringUtil.normalizePhoneNumber(phoneNumber);
    }

    public void validatePhoneFields() {
        StringUtil.validateRequiredTogether(countryCode, phoneNumber,
                () -> ApiRuntimeException.type(UserErrorCode.PHONE_FIELDS_REQUIRED_TOGETHER));
    }

    /** 역할별로 확정한 할당량(TenantQuotaPolicy.resolveUserQuota)을 담은 커맨드 */
    public AdminRegisterUserCommand withTotalQuotaByte(long resolvedTotalQuotaByte) {
        return new AdminRegisterUserCommand(userId, encPwd, userName, resolvedTotalQuotaByte, roles,
                profileImageFile, countryCode, phoneNumber);
    }
}
