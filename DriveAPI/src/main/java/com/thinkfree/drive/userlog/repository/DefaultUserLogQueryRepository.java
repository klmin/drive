package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.userlog.data.UserLogData;
import com.thinkfree.drive.userlog.enums.ActivityType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_user_log 읽기 — 구 UserLogMapper.xml 의 findAll(커서 목록)·existsByUserSeqAndActivityType 을 JPA 로(§1 v2 "JPA 우선", JdbcClient 0). 쓰기는 {@link DefaultUserLogRepository}.
 * 목록은 DTO 투영(JPQL 생성자 식)이라 영속 컨텍스트에 엔티티가 남지 않음 → detach 불필요. 구 XML 의 {@code <if ci>} 는 JPQL {@code (:ci is null or …)} 로.
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserLogQueryRepository implements UserLogQueryRepository {

    private final UserLogJpaRepository jpaRepository;

    @Override
    public List<UserLogData> findAll(Integer userSeq, CursorPagingRequest pagingRequest) {
        return jpaRepository.findAll(userSeq, pagingRequest.ci(), Limit.of(pagingRequest.ps()));
    }

    @Override
    public boolean existsChangePasswordLog(Integer userSeq) {
        return jpaRepository.existsByUserSeqAndActivityType(userSeq, ActivityType.CHANGE_PASSWORD);
    }

    @Override
    public boolean existsLoginLog(Integer userSeq) {
        return !jpaRepository.existsByUserSeqAndActivityType(userSeq, ActivityType.LOGIN);
    }
}
