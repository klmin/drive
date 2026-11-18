package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.userlog.data.UserLogData;

import java.util.List;

/**
 * tb_user_log 읽기 — {@code UserLogFindService}(findMyLogs)·{@code UserLogService}(hasChangedPassword·isFirstLogin) 가 주입. 쓰기는 {@link UserLogRepository}.
 */
public interface UserLogQueryRepository {

    /** 사용자 로그 커서 목록 — register_date DESC, ci(user_log_seq) 미만, ps 건 */
    List<UserLogData> findAll(Integer userSeq, CursorPagingRequest pagingRequest);

    /** CHANGE_PASSWORD 로그가 1건이라도 있으면 true */
    boolean existsChangePasswordLog(Integer userSeq);

    /** LOGIN 로그가 하나도 없으면 true(= 첫 로그인) — 이름과 반대 의미지만 기존 계약 유지 */
    boolean existsLoginLog(Integer userSeq);
}
