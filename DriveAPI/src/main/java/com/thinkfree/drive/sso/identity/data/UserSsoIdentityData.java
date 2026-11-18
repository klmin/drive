package com.thinkfree.drive.sso.identity.data;

import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SSO identity + 사용자 상태 조인 결과(구 UserSsoIdentityMapper.findWithUser). JPQL 생성자 식({@code select new …Data(i.ssoIdentitySeq, i.userSeq, u.tenantSeq, u.status, u.mfaStatus, u.signupType)})이
 * 쓰는 public 전체 인자 생성자 — 필드 순서를 바꾸면 {@code UserSsoIdentityJpaRepository.findWithUser} 의 JPQL 도 함께 바꿀 것.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserSsoIdentityData {
    private Long ssoIdentitySeq;
    private Integer userSeq;
    private Integer tenantSeq;
    private UserStatus userStatus;
    private MfaStatus mfaStatus;
    private SignupType signupType;
}
