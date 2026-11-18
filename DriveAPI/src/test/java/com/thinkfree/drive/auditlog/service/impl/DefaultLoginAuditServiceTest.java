package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * LoginAuditService가 각 이벤트에 대해 ThreadLocal 홀더에 채우는 context 내용을 검증한다.
 * (로그인 처리 중이라 인증 컨텍스트가 없어 actor는 대상 본인으로 override된다.)
 */
@DisplayName("DefaultLoginAuditService context 기록 내용 테스트")
class DefaultLoginAuditServiceTest extends AuditMockTest {

    private final LoginAuditService loginAuditService = new DefaultLoginAuditService();

    private UserDetailData userMock(int seq, RoleType role) {
        UserDetailData u = Mockito.mock(UserDetailData.class);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        return u;
    }

    @Test
    @DisplayName("recordLoginSuccess - LOGIN_SUCCESS, 행위자=대상 본인 override")
    void recordLoginSuccess_writesContext() {
        loginAuditService.recordLoginSuccess(userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.LOGIN_SUCCESS);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getObjectUserId()).isEqualTo("user20@thinkfree.com");
        assertThat(ctx.getActorUserSeq()).isEqualTo(20);
        assertThat(ctx.getActorUserId()).isEqualTo("user20@thinkfree.com");
        assertThat(ctx.getActorUserName()).isEqualTo("사용자20");
    }

    @Test
    @DisplayName("recordLoginFailure - LOGIN_FAILED, 행위자=대상 본인 override")
    void recordLoginFailure_writesContext() {
        loginAuditService.recordLoginFailure(userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.LOGIN_FAILED);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getObjectUserId()).isEqualTo("user20@thinkfree.com");
        assertThat(ctx.getActorUserSeq()).isEqualTo(20);
    }

    @Test
    @DisplayName("recordAccountLocked - ACCOUNT_LOCKED_STATUS_CHANGE, SYSTEM·UNLOCKED→LOCKED·outcome SUCCESS")
    void recordAccountLocked_writesContext() {
        loginAuditService.recordAccountLocked(userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.ACCOUNT_LOCKED_STATUS_CHANGE);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getObjectUserId()).isEqualTo("user20@thinkfree.com");
        // 사용자 요청이 아니라 내부 정책 판단 → SYSTEM
        assertThat(ctx.getAccessMethod()).isEqualTo(AccessMethod.SYSTEM);
        assertThat(ctx.getBeforeValue()).isEqualTo("UNLOCKED");
        assertThat(ctx.getAfterValue()).isEqualTo("LOCKED");
        // 상태 변화는 로그인 실패 예외 경로에서 기록돼도 outcome을 SUCCESS로 고정
        assertThat(ctx.getOutcomeStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("recordAccountUnlocked - ACCOUNT_LOCKED_STATUS_CHANGE, LOCKED→UNLOCKED")
    void recordAccountUnlocked_writesContext() {
        loginAuditService.recordAccountUnlocked(userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.ACCOUNT_LOCKED_STATUS_CHANGE);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getBeforeValue()).isEqualTo("LOCKED");
        assertThat(ctx.getAfterValue()).isEqualTo("UNLOCKED");
    }
}
