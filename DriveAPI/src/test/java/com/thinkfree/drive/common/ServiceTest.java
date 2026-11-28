package com.thinkfree.drive.common;

import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auth.token.refresh.repository.RefreshTokenRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.role.entity.UserRole;
import com.thinkfree.drive.user.role.repository.UserRoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@DriveIntegrationTest
@Transactional
public class ServiceTest {
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserQueryRepository userQueryRepository;

    @Autowired
    private EntityManager serviceTestEntityManager;

    @Autowired
    private JdbcTemplate serviceTestJdbc;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    /** 서비스를 Aspect 없이 직접 불러 감사 로그 컨텍스트(ThreadLocal)가 쌓인다 — 같은 스레드의 다음 테스트로 새지 않게 */
    @AfterEach
    void clearAuditLogContext() {
        AuditLogContextHolder.clear();
    }

    /**
     * 테스트 계정 INSERT + 그 계정 역할(role) 1개 부여 — 로그인 조회·토큰 발급·필터가 tb_user_role 을 보므로(다중 role, 10-23)
     * 로그인까지 가는 테스트는 저장소 INSERT 대신 이것을 쓴다.
     */
    protected User insertUserWithRole(User user, RoleType role) {
        userRepository.insertUserWithSeatCheck(user, false, 0);
        userRoleRepository.grant(UserRole.grant(user.getUserSeq(), role, user.getRegisterUserSeq()));
        return user;
    }

    /**
     * 세션의 refresh 토큰이 DB 에 그 값으로 있고 만료 전인지(재발급 판정과 같은 조건) — 읽기 전용 Query 리포가 없어 DB 직접 확인.
     * 재발급(RTR)은 커밋 때 변경 감지라 테스트 트랜잭션 안에서는 먼저 flush 해 UPDATE 를 내보낸 뒤 읽는다.
     */
    protected boolean refreshTokenValidInDb(String sessionId, String refreshToken) {
        serviceTestEntityManager.flush();
        Integer n = serviceTestJdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_refresh_token WHERE session_id = ? AND refresh_token = ? AND expiration_date >= CURRENT_TIMESTAMP",
                Integer.class, sessionId, refreshToken);
        return n != null && n > 0;
    }
}
