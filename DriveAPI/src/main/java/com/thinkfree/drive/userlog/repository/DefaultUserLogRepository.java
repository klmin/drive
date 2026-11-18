package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.userlog.entity.UserLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * tb_user_log 쓰기 — 구 UserLogMapper.xml 의 insertUserLog 를 JPA 로(§1 v2 "JPA 우선", JdbcClient 0). 읽기는 {@link DefaultUserLogQueryRepository}.
 * {@code save}(IDENTITY 라 저장 순간 INSERT) — identity 키(bigint → Long)가 채워지고 register_date 는 {@code insertable=false} 라 DB DEFAULT(구 XML 동형). user_seq FK 위반은 {@code DataIntegrityViolationException}.
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserLogRepository implements UserLogRepository {

    private final UserLogJpaRepository jpaRepository;

    @Override
    public void insertUserLog(UserLog log) {
        jpaRepository.save(log);
    }
}
