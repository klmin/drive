package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.userlog.entity.UserLog;

/**
 * tb_user_log 쓰기 — {@code UserLogService.registerUserLog}(로그인·로그아웃·비밀번호 변경 로그) 가 주입. 읽기는 {@link UserLogQueryRepository}.
 */
public interface UserLogRepository {

    /** JPA save(IDENTITY 라 저장 순간 INSERT) — identity 키가 엔티티에 채워진다(register_date 는 DB DEFAULT) */
    void insertUserLog(UserLog log);
}
