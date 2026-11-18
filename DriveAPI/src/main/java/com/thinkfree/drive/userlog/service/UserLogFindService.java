package com.thinkfree.drive.userlog.service;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.page.response.CursorPaginationResponse;
import com.thinkfree.drive.userlog.data.UserLogData;
import com.thinkfree.drive.userlog.repository.UserLogQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** tb_user_log 조회 — 쓰기·로그인 판정은 {@link UserLogService} */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserLogFindService {

    private final UserLogQueryRepository userLogQueryRepository;

    /**
     * userSeq 기준 로그 조회. 호출자(controller)가 권한/테넌트 검증 책임.
     * - 본인 조회: 인증 토큰의 userSeq 사용
     * - 관리자 조회: admin path 라우팅이 어드민 권한을 보장 (Spring Security)
     */
    public CursorPaginationResponse<UserLogData> findMyLogs(Integer userSeq, CursorPagingRequest pagingRequest) {

        var logs = userLogQueryRepository.findAll(userSeq, pagingRequest);

        var page = CursorApiPage.of(logs, pagingRequest.ps(), null, UserLogData::getUserLogSeq, null);

        return CursorPaginationResponse.of(logs, page);
    }
}
