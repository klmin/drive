package com.thinkfree.drive.util;

import com.thinkfree.drive.global.security.CustomUserDetails;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class SecurityUtil {

    public static Integer getUserSeq() {
        CustomUserDetails customUserDetails = getUserDetails();
        return customUserDetails == null ? null : customUserDetails.getUserSeq();
    }

    /**
     * 인증된 사용자를 전제하는 진입점용(시큐리티 체인이 미인증을 이미 차단한 경로) —
     * null일 수 없는 상황을 타입으로 표현해 호출부의 null 체크를 없앤다.
     * 컨텍스트가 비어 있으면 NPE로 크게 실패한다 (호출부가 잡지 않는 예외).
     */
    public static CustomUserDetails requireUserDetails() {
        return Objects.requireNonNull(getUserDetails(), "인증된 사용자가 없습니다");
    }

    public static CustomUserDetails getUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof CustomUserDetails customUserDetails) {
                return customUserDetails;
            }
        }
        
        return null;
    }
}
