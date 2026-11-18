package com.thinkfree.drive.auditlog.service;

import com.thinkfree.drive.user.data.UserIdentity;

/**
 * LOGIN 카테고리 감사 로그 기록의 단일 진입점. 호출부는 {@link com.thinkfree.drive.auditlog.support.UserAuditSupport}를
 * 직접 부르지 않고 이 서비스만 의존한다(Support는 내부 구현).
 *
 * <p>로그인·로그아웃 처리 중에는 SecurityContextHolder에 인증 정보가 없어(로그아웃은 인증 없이 받음), actor는 대상 User 본인으로 override된다.
 */
public interface LoginAuditService {

    /** 로그인 성공(LOGIN_SUCCESS). */
    void recordLoginSuccess(UserIdentity user);

    /** 로그아웃(LOGOUT) — 쿠키의 access 토큰이 유효할 때만 호출된다. */
    void recordLogout(UserIdentity user);

    /** 로그인 실패(LOGIN_FAILED). */
    void recordLoginFailure(UserIdentity user);

    /** 로그인 5회 실패로 계정이 잠김(ACCOUNT_LOCKED_STATUS_CHANGE). */
    void recordAccountLocked(UserIdentity user);

    /** 관리자가 비밀번호를 강제 변경하면서 잠긴 계정을 함께 해제(ACCOUNT_LOCKED_STATUS_CHANGE, LOCKED→UNLOCKED). */
    void recordAccountUnlocked(UserIdentity user);
}
