package com.thinkfree.drive.auditlog.lookup.user;

import com.thinkfree.drive.user.data.UserDetailData;

/**
 * 감사 로그 기록에 필요하지만 비즈니스 로직상으로는 조회할 필요가 없는 User 정보를
 * 조회하는 공용 창구. 새 쿼리를 추가하지 않고 기존 UserFindService 호출만 감싼다.
 */
public interface UserAuditLookupService {
    UserDetailData findUser(Integer userSeq);
}
