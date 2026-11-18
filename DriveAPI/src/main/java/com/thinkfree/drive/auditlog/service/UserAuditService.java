package com.thinkfree.drive.auditlog.service;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.support.UserAuditSupport;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserIdentity;

/**
 * USER_MANAGEMENT 카테고리 감사 로그 기록의 단일 진입점. 호출부는 {@link UserAuditSupport}와
 * {@link com.thinkfree.drive.auditlog.support.UserInfoUpdateAuditSupport}를 직접 부르지 않고 이 서비스만 의존한다(Support는 내부 구현).
 *
 * <p>LOGIN 카테고리의 ACCOUNT_LOCKED_STATUS_CHANGE(계정 잠금/해제)는 {@link LoginAuditService}가 담당한다 —
 * {@code UserAuditSupport}를 내부적으로 공유하지만 카테고리가 다르므로 이 서비스는 다루지 않는다.
 */
public interface UserAuditService {

    /**
     * before/after가 없는 USER_MANAGEMENT 이벤트를 기록한다.
     * <p>USER_TWOFACTOR_RESET, USER_DELETE/ADMIN_DELETE, USER_RESTORE/ADMIN_RESTORE,
     * USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE, USER_PERMANENT_DELETE/ADMIN_PERMANENT_DELETE(단건),
     * USER_CREATE/ADMIN_CREATE(CSV·일괄 등록 fan-out)
     */
    void record(EventName eventName, UserIdentity user);

    /**
     * before/after가 있는 USER_MANAGEMENT 이벤트를 기록한다.
     * <p>USER_STATUS_CHANGE/ADMIN_STATUS_CHANGE
     */
    void record(EventName eventName, UserIdentity user, String beforeValue, String afterValue);

    /**
     * 미인증 경로(비밀번호 재설정 링크)에서 행위자를 대상 본인으로 override해 기록한다.
     * <p>USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE
     */
    void recordSelfActed(EventName eventName, UserIdentity user);

    /**
     * 삭제 대기 목록 fan-out 영구삭제를 기록한다. actor(행위자)와 tenantSeq(대상 소유 테넌트)를 명시로 override한다.
     * <p>USER_PERMANENT_DELETE/ADMIN_PERMANENT_DELETE
     */
    void record(EventName eventName, UserPendingDeleteData target, UserIdentity actor, Integer tenantSeq);

    /**
     * 단건 계정 등록을 기록한다. 방금 생성한 유저라 User 엔티티 없이 요청에 있던 값으로 기록한다.
     * <p>USER_CREATE/ADMIN_CREATE(단건 등록)
     */
    void record(EventName eventName, Long objectSeq, String objectUserId, String objectName);

    /**
     * 관리자에 의한 정보 수정을 기록한다. 바뀐 필드만 before/after JSON에 담고, 변경이 없으면 기록하지 않는다.
     * <p>USER_INFO_UPDATE/ADMIN_INFO_UPDATE(관리자 단건/일괄 수정)
     */
    void recordInfoUpdate(UserDetailData before, String newName, Long newStorageQuota, String newCountryCode, String newPhone);

    /**
     * 본인에 의한 정보 수정을 기록한다. 이름/저장용량은 본인이 바꿀 수 없어 국가코드/전화번호만 비교한다.
     * <p>USER_INFO_UPDATE(본인 정보 수정)
     */
    void recordSelfInfoUpdate(UserDetailData before, String newCountryCode, String newPhone);
}
