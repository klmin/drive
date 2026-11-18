package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.jooq.tables.records.TbUserRoleRecord;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_USER_ROLE;

/**
 * tb_user_role 쓰기 — jOOQ. 읽기는 {@link DefaultUserRoleQueryRepository}.
 * <ul>
 *   <li>{@code grant}·{@code grantAll} = INSERT. 여러 행은 {@link JooqBatches} 레코드 배치(TbUserRoleRecord, 1000행씩 한 PreparedStatement·한 번 왕복) — 사용자 일괄 수정과 같은 패턴</li>
 *   <li>{@code revoke} = DELETE, {@code deleteByUserSeqList} = 계정 완전 삭제 cascade 의 DELETE … IN(한 문장)</li>
 *   <li>영속성 컨텍스트를 거치지 않아 flush·detach 가 필요 없고, 같은 트랜잭션의 읽기(JPQL·jOOQ)에 바로 보인다</li>
 *   <li>등록 감사는 {@link JdbcAudit} — register_user_seq 는 시큐리티 사용자 우선(없으면 {@link UserRole#grant} 에 넘긴 값), register_date 는 현재 시각 초 절삭</li>
 * </ul>
 * 이미 있는 (user_seq, role_type) 부여는 PK 위반 → {@code DuplicateKeyException}({@code DataIntegrityViolationException}).
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserRoleRepository implements UserRoleRepository {

    private final DSLContext dsl;

    @Override
    public void grant(UserRole userRole) {
        grantAll(List.of(userRole));
    }

    @Override
    public void grantAll(List<UserRole> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return;
        }
        Instant registerDate = JdbcAudit.now();
        JooqBatches.insert(dsl, userRoles, userRole -> toRecord(userRole, registerDate));
    }

    /** 부여 한 행 — 등록자는 시큐리티 사용자 우선(없으면 선언 값) */
    private TbUserRoleRecord toRecord(UserRole userRole, Instant registerDate) {
        TbUserRoleRecord r = dsl.newRecord(TB_USER_ROLE);
        r.setUserSeq(userRole.getUserSeq());
        r.setRoleType(userRole.getRoleType());
        r.setRegisterUserSeq(JdbcAudit.userSeqOr(userRole.getRegisterUserSeq()));
        r.setRegisterDate(registerDate);
        return r;
    }

    @Override
    public int revoke(Integer userSeq, RoleType roleType) {
        return dsl.deleteFrom(TB_USER_ROLE)
                .where(TB_USER_ROLE.USER_SEQ.eq(userSeq))
                .and(TB_USER_ROLE.ROLE_TYPE.eq(roleType))
                .execute();
    }

    /** 빈 목록은 {@code IN ()} 를 만들지 않도록 0(다른 cascade 삭제와 동일) */
    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return dsl.deleteFrom(TB_USER_ROLE)
                .where(TB_USER_ROLE.USER_SEQ.in(userSeqList))
                .execute();
    }
}
