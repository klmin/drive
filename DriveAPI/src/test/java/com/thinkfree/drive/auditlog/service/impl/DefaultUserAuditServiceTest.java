package com.thinkfree.drive.auditlog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.auditlog.support.UserInfoUpdateAuditSupport;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * UserAuditService가 각 이벤트에 대해 ThreadLocal 홀더에 채우는 context 내용을 검증한다.
 * 호출부(AdminUseCase 등)가 <b>어떤 인자로 이 서비스를 호출하는지</b>는 각 UseCaseAuditTest에서 다룬다.
 */
@DisplayName("DefaultUserAuditService context 기록 내용 테스트")
class DefaultUserAuditServiceTest extends AuditMockTest {

    private final UserRoleFindService userRoleFindService = Mockito.mock(UserRoleFindService.class);
    private final UserAuditService userAuditService =
            new DefaultUserAuditService(new UserInfoUpdateAuditSupport(new ObjectMapperUtil(new ObjectMapper()), userRoleFindService));

    private UserDetailData userMock(int seq, RoleType role) {
        UserDetailData u = Mockito.mock(UserDetailData.class);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        // ADMIN_/USER_ 분기 = 대상의 ADMIN 역할 보유(tb_user_role)
        given(userRoleFindService.holds(seq, RoleType.ADMIN)).willReturn(role == RoleType.ADMIN);
        return u;
    }

    // EventName: (before/after 없음, 예: USER_TWOFACTOR_RESET)
    @Test
    @DisplayName("record(eventName, user) - before/after 없이 대상 정보만 기록한다")
    void record_user_writesContext() {
        userAuditService.record(EventName.USER_TWOFACTOR_RESET, userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_TWOFACTOR_RESET);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getObjectUserId()).isEqualTo("user20@thinkfree.com");
        assertThat(ctx.getObjectName()).isEqualTo("사용자20");
        assertThat(ctx.getBeforeValue()).isNull();
        assertThat(ctx.getAfterValue()).isNull();
    }

    // EventName: USER_STATUS_CHANGE/ADMIN_STATUS_CHANGE
    @Test
    @DisplayName("record(eventName, user, before, after) - 상태 변화 전/후 값을 기록한다")
    void record_userWithBeforeAfter_writesContext() {
        userAuditService.record(EventName.USER_STATUS_CHANGE, userMock(20, RoleType.USER), "INACTIVE", "ACTIVE");

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_STATUS_CHANGE);
        assertThat(ctx.getBeforeValue()).isEqualTo("INACTIVE");
        assertThat(ctx.getAfterValue()).isEqualTo("ACTIVE");
    }

    // EventName: USER_PASSWORD_CHANGE (미인증 경로 - 행위자를 대상 본인으로 override)
    @Test
    @DisplayName("recordSelfActed - 행위자를 대상 본인으로 override해 기록한다")
    void recordSelfActed_writesContextWithSelfActor() {
        userAuditService.recordSelfActed(EventName.USER_PASSWORD_CHANGE, userMock(20, RoleType.USER));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_PASSWORD_CHANGE);
        assertThat(ctx.getBeforeValue()).isNull();
        assertThat(ctx.getAfterValue()).isNull();
        assertThat(ctx.getActorUserSeq()).isEqualTo(20);
        assertThat(ctx.getActorUserId()).isEqualTo("user20@thinkfree.com");
        assertThat(ctx.getActorUserName()).isEqualTo("사용자20");
    }

    // EventName: USER_PERMANENT_DELETE/ADMIN_PERMANENT_DELETE (삭제 대기 목록 fan-out)
    @Test
    @DisplayName("record(eventName, target, actor, tenantSeq) - actor/tenantSeq를 명시로 override해 기록한다")
    void record_pendingDeleteTarget_writesContextWithActorOverride() {
        var target = Mockito.mock(UserPendingDeleteData.class);
        given(target.getUserSeq()).willReturn(20);
        given(target.getUserId()).willReturn("user20@thinkfree.com");
        given(target.getUserName()).willReturn("사용자20");

        UserDetailData actor = userMock(1, RoleType.ADMIN);

        userAuditService.record(EventName.USER_PERMANENT_DELETE, target, actor, 5);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_PERMANENT_DELETE);
        assertThat(ctx.getObjectSeq()).isEqualTo(20L);
        assertThat(ctx.getTenantSeq()).isEqualTo(5);
        assertThat(ctx.getActorUserSeq()).isEqualTo(1);
        assertThat(ctx.getActorUserId()).isEqualTo("user1@thinkfree.com");
    }

    // EventName: USER_CREATE/ADMIN_CREATE (단건 등록 - User 엔티티 없이 스칼라 값)
    @Test
    @DisplayName("record(eventName, objectSeq, objectUserId, objectName) - 스칼라 값만으로 기록한다")
    void record_scalarValues_writesContext() {
        userAuditService.record(EventName.USER_CREATE, 100L, "new@thinkfree.com", "새사용자");

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_CREATE);
        assertThat(ctx.getObjectSeq()).isEqualTo(100L);
        assertThat(ctx.getObjectUserId()).isEqualTo("new@thinkfree.com");
        assertThat(ctx.getObjectName()).isEqualTo("새사용자");
    }

    // EventName: USER_INFO_UPDATE/ADMIN_INFO_UPDATE (관리자 수정 - 바뀐 필드만 diff)
    @Test
    @DisplayName("recordInfoUpdate - 바뀐 필드만 before/after JSON에 담고, objectName은 변경 후 이름을 쓴다")
    void recordInfoUpdate_changed_writesDiffContext() {
        UserDetailData before = userMock(20, RoleType.USER);
        given(before.getTotalQuotaByte()).willReturn(100L);
        given(before.getCountryCode()).willReturn(null);
        given(before.getPhoneNumber()).willReturn(null);

        userAuditService.recordInfoUpdate(before, "새사용자20", 100L, null, null);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_INFO_UPDATE);
        assertThat(ctx.getObjectName()).isEqualTo("새사용자20");
        assertThat(ctx.getBeforeValue()).contains("사용자20");
        assertThat(ctx.getAfterValue()).contains("새사용자20");
    }

    @Test
    @DisplayName("recordInfoUpdate - 대상이 ADMIN이면 ADMIN_INFO_UPDATE로 기록한다")
    void recordInfoUpdate_adminTarget_recordsAdminEvent() {
        UserDetailData before = userMock(21, RoleType.ADMIN);
        given(before.getTotalQuotaByte()).willReturn(100L);
        given(before.getCountryCode()).willReturn(null);
        given(before.getPhoneNumber()).willReturn(null);

        userAuditService.recordInfoUpdate(before, "새관리자21", 100L, null, null);

        assertThat(AuditLogContextHolder.getAll().get(0).getEventName()).isEqualTo(EventName.ADMIN_INFO_UPDATE);
    }

    @Test
    @DisplayName("recordInfoUpdate - 바뀐 필드가 없으면 기록하지 않는다")
    void recordInfoUpdate_unchanged_recordsNothing() {
        UserDetailData before = userMock(20, RoleType.USER);
        given(before.getTotalQuotaByte()).willReturn(100L);
        given(before.getCountryCode()).willReturn(null);
        given(before.getPhoneNumber()).willReturn(null);

        userAuditService.recordInfoUpdate(before, "사용자20", 100L, null, null);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: USER_INFO_UPDATE (본인 수정 - 국가코드/전화번호만 비교 대상)
    @Test
    @DisplayName("recordSelfInfoUpdate - 국가코드/전화번호 변경분만 diff로 담는다")
    void recordSelfInfoUpdate_changed_writesDiffContext() {
        UserDetailData before = userMock(20, RoleType.USER);
        given(before.getCountryCode()).willReturn("+82");
        given(before.getPhoneNumber()).willReturn("010-1111-1111");

        userAuditService.recordSelfInfoUpdate(before, "+1", "010-2222-2222");

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.USER_INFO_UPDATE);
        assertThat(ctx.getObjectName()).isEqualTo("사용자20"); // 본인은 이름 변경 불가 → 기존 이름
        assertThat(ctx.getBeforeValue()).contains("+82").contains("010-1111-1111");
        assertThat(ctx.getAfterValue()).contains("+1").contains("010-2222-2222");
    }

    @Test
    @DisplayName("recordSelfInfoUpdate - 국가코드/전화번호가 그대로면 기록하지 않는다")
    void recordSelfInfoUpdate_unchanged_recordsNothing() {
        UserDetailData before = userMock(20, RoleType.USER);
        given(before.getCountryCode()).willReturn("+82");
        given(before.getPhoneNumber()).willReturn("010-1111-1111");

        userAuditService.recordSelfInfoUpdate(before, "+82", "010-1111-1111");

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }
}
