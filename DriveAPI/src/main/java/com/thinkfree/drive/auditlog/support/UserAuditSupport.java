package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserIdentity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// LOGIN 카테고리(1건: ACCOUNT_LOCKED_STATUS_CHANGE)와 USER_MANAGEMENT 카테고리(15건 전부)가
// 둘 다 User 엔티티(또는 그에 준하는 값)에서 동일한 필드를 추출하므로 이 클래스를 공유해서 사용함
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserAuditSupport {

    // before/after가 없는 이벤트: USER_TWOFACTOR_RESET, USER_CREATE(CSV fan-out),
    // USER_DELETE/ADMIN_DELETE, USER_RESTORE/ADMIN_RESTORE, USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE,
    // USER_PERMANENT_DELETE/ADMIN_PERMANENT_DELETE(User 버전)
    // (USER_CREATE/ADMIN_CREATE의 registerUser 단건 생성 경로는 User 엔티티가 없어 아래 Long/String 오버로드를 씀)
    public static void record(EventName eventName, UserIdentity user) {
        record(eventName, user, null, null);
    }

    // before/after가 있는 이벤트: USER_INFO_UPDATE/ADMIN_INFO_UPDATE, USER_STATUS_CHANGE/ADMIN_STATUS_CHANGE
    // (행위자는 인증된 관리자 - Aspect가 SecurityContextHolder에서 채움)
    public static void record(EventName eventName, UserIdentity user, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(user.getUserSeq().longValue())
                .objectUserId(user.getUserId())
                .objectName(user.getUserName())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // 행위자(로그인 시도자)와 대상이 동일 인물이고, 로그인 처리 중이라 SecurityContextHolder에 인증 정보가 없어
    // actor를 직접 override하는 케이스(LOGIN_SUCCESS/LOGIN_FAILED). accessMethod는 요청 경로 기준(WEB 등) 기본 판정.
    public static void recordSelfActed(EventName eventName, UserIdentity user, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(user.getUserSeq().longValue())
                .objectUserId(user.getUserId())
                .objectName(user.getUserName())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .actorUserSeq(user.getUserSeq())
                .actorUserId(user.getUserId())
                .actorUserName(user.getUserName())
                .build());
    }

    // 계정 잠김(로그인 5회 실패로 UNLOCKED→LOCKED). 로그인 실패 예외 경로에서 기록되지만 "잠금 상태 변화"는
    // 정상적으로 수행된 것이므로 outcome을 SUCCESS로 고정한다(상태 변화 이벤트 = SUCCESS 정책). "유저가 잠가달라
    // 요청한 게 아니라 내부 로직이 판단해 잠근 것"이라 accessMethod=SYSTEM, actor는 대상 유저 본인.
    public static void recordAccountLocked(UserIdentity user) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.ACCOUNT_LOCKED_STATUS_CHANGE)
                .objectSeq(user.getUserSeq().longValue())
                .objectUserId(user.getUserId())
                .objectName(user.getUserName())
                .beforeValue("UNLOCKED")
                .afterValue("LOCKED")
                .actorUserSeq(user.getUserSeq())
                .actorUserId(user.getUserId())
                .actorUserName(user.getUserName())
                .accessMethod(AccessMethod.SYSTEM)
                .outcomeStatus("SUCCESS")
                .build());
    }

    // USER_PERMANENT_DELETE/ADMIN_PERMANENT_DELETE(대기삭제 목록 fan-out, UserPendingDeleteData 버전).
    // actor(행위자)와 tenantSeq를 명시로 override한다. 이 경로는 관리자 수동(WEB)과 스케줄러 자동삭제(SYSTEM)가 공유하는데:
    //  - actor는 두 경로 다 dto.actorUserSeq()로 넘어온 값을 조회한 것(WEB=관리자, 스케줄러=슈퍼어드민).
    //  - tenantSeq는 대상 계정의 테넌트(스케줄러는 여러 테넌트를 한 번에 처리하므로 row별로 달라짐).
    //  - accessMethod는 override하지 않음 → Aspect가 요청 유무로 자동 판정(WEB엔 요청 있음→WEB, 스케줄러엔 없음→SYSTEM).
    public static void record(EventName eventName, UserPendingDeleteData target, UserIdentity actor, Integer tenantSeq) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(target.getUserSeq().longValue())
                .objectUserId(target.getUserId())
                .objectName(target.getUserName())
                .tenantSeq(tenantSeq)
                .actorUserSeq(actor.getUserSeq())
                .actorUserId(actor.getUserId())
                .actorUserName(actor.getUserName())
                .build());
    }

    // USER_CREATE/ADMIN_CREATE(registerUser 단건 생성) 전용.
    // 방금 생성한 유저라 필요한 값(userId/userName)이 이미 request에 있어 재조회하지 않음
    public static void record(EventName eventName, Long objectSeq, String objectUserId, String objectName) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectUserId(objectUserId)
                .objectName(objectName)
                .build());
    }

    // objectName(유저명)을 명시로 받는 before/after 버전.
    // USER_INFO_UPDATE/ADMIN_INFO_UPDATE는 Resource 표시(objectName)를 "변경 전"이 아니라 "변경 후" 이름으로 남겨야 해서,
    // before 엔티티의 이름을 그대로 쓰는 record(EventName, User, before, after)와 달리 새 이름을 직접 넘긴다.
    public static void record(EventName eventName, Long objectSeq, String objectUserId, String objectName,
                               String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectUserId(objectUserId)
                .objectName(objectName)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }
}
