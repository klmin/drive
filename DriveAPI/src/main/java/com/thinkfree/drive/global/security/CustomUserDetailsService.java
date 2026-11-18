package com.thinkfree.drive.global.security;

import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 인증 주체 로드 — 역할을 함께 받는 경로만 둔다(다중 role). 이메일만 받는 스프링 UserDetailsService 는 구현하지 않는다:
 * 이메일만으로는 이번 요청의 역할(로그인 화면·토큰 role 클레임)을 정할 수 없고, 로그인은 AuthService 가 직접 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomUserDetailsService {
    private final UserQueryRepository userQueryRepository;

    /**
     * 웹 JWT 요청 — 동작 역할 = 토큰 role 클레임. ACTIVE 이고 그 역할을 보유한 계정만 한 번의 조회로 로드하고,
     * 아니면(없음·비활성·삭제·역할 회수) {@link UsernameNotFoundException} — 필터가 인증 없이 넘겨 401. authority·보유 역할 모두 그 1개.
     */
    public UserDetails loadUserByUserSeq(Integer userSeq, RoleType actingRole) throws UsernameNotFoundException {
        return loadUserByUserSeq(userSeq, actingRole, AuthSource.INTERNAL);
    }

    /**
     * 역할 1개로 인증하는 경로 공통(웹 JWT = INTERNAL, MCP API Key = 키 역할·EXTERNAL).
     * AuthSource는 "어떤 채널로 인증됐나"의 속성이라 호출한 인증 필터가 결정한다 —
     * 외부 채널(API Key·제3자 OAuth 클라이언트)은 EXTERNAL을 넘겨 외부 호출 차단 로직(예: ADMIN 등록 거부)이 동작하게 한다.
     */
    public UserDetails loadUserByUserSeq(Integer userSeq, RoleType actingRole, AuthSource authSource) throws UsernameNotFoundException {
        UserDetailData user = userQueryRepository.findActiveDetailHoldingRole(userSeq, actingRole);
        if (user == null) {
            throw new UsernameNotFoundException("User not found, inactive or role not held");
        }
        UserDetailDto userDetailDto = new UserDetailDto(user.getUserSeq(), user.getUserId(), user.getUserName(), actingRole.getAuthority(),
                authSource, user.getTenantSeq(), null, Set.of(actingRole));
        return new CustomUserDetails(userDetailDto);
    }
}
