package com.thinkfree.drive.sso.protocol.oidc.service;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.auth.result.LoginTokenResult;
import com.thinkfree.drive.auth.service.AuthService;
import com.thinkfree.drive.common.validation.EmailValidator;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.signup.dto.SignupDto;
import com.thinkfree.drive.signup.result.SignupResult;
import com.thinkfree.drive.signup.service.SignupService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityService;
import com.thinkfree.drive.sso.protocol.oidc.converter.OidcSignupConverter;
import com.thinkfree.drive.sso.protocol.oidc.dto.OidcSignupDto;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.*;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OidcSignupService implements SignupService {
    
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserFindService userFindService;
    private final AuthService authService;
    private final QuotaService quotaService;
    private final OidcSignupConverter oidcSignupConverter;
    private final UserSsoIdentityService userSsoIdentityService;
    private final LicenseService licenseService;
    private final UserRoleService userRoleService;
    private final TenantPolicy tenantPolicy;

    @Override
    public SignupType supports() {
        return SignupType.SSO_OIDC;
    }
    
    /**
     * 지금 사용안함.
     * @param dto
     * @param httpServletRequest
     * @return
     */
    @Override
    public SignupResult signup(SignupDto dto, HttpServletRequest httpServletRequest) {
        OidcSignupDto signupDto = (OidcSignupDto)dto;
        tenantPolicy.require(signupDto.tenantSeq(), TenantFeature.USER_REGISTER);

        String email = signupDto.email();
        String userId = StringUtils.hasText(email) ? email : signupDto.requestEmail();
        String userName = signupDto.requestName();
        
        EmailValidator.validateOrThrow(userId);
        userService.exists(signupDto.tenantSeq(), userId);
        
        long totalQuotaByte = DriveConstants.DEFAULT_USER_QUOTA_BYTE; // 정책: 사용자 기본 할당량 1GB
        quotaService.ensureCanAssignQuotaIncrease(totalQuotaByte);

        User user = User.createUser(userId, signupDto.tenantSeq(), null, SignupType.SSO_OIDC, userName, UserStatus.ACTIVE,
                                    totalQuotaByte, DriveConstants.SUPER_ADMIN_USER_SEQ, null, null, null);
        
        var seatPolicy = licenseService.getSeatPolicy(signupDto.tenantSeq());
        boolean inserted = userRepository.insertUserWithSeatCheck(user, seatPolicy.isSeat(), seatPolicy.constrainValue());
        log.info("[OIDC 사용자 등록] userId={}, isSeat={}, constrainValue={}, inserted={}", user.getUserId(), seatPolicy.isSeat(), seatPolicy.constrainValue(), inserted);
        if (!inserted) {
            throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
        }
        userRoleService.assignInitialRoles(UserRoleAssignmentDto.single(user.getUserSeq(), RoleType.USER, user.getRegisterUserSeq()));

        UserSsoIdentitySaveDto identity = oidcSignupConverter.toUserSsoIdentitySaveDto(signupDto, user.getUserSeq());
        userSsoIdentityService.save(identity);
        
        // 로그인 토큰·감사에는 방금 INSERT 한 계정을 DTO 로 다시 읽어 넘긴다(같은 트랜잭션 — PK 조회 1회)
        UserDetailData loginIdentity = userFindService.findDetailByUserSeq(user.getUserSeq());
        LoginTokenResult result = authService.issueLoginTokensAndLog(loginIdentity, RoleType.USER, httpServletRequest);
        
        return new SignupResult(
                user.getUserSeq(),
                userId,
                userName,
                totalQuotaByte,
                0L,
                List.of(RoleType.USER), // SSO 가입은 USER 1개만 부여(위 assignInitialRoles)
                UserLanguage.AUTO,
                DefaultLandingPage.HOME,
                result.accessToken(),
                result.refreshToken()
        );
    }
}
