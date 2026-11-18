package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.query.*;

import java.util.List;

/**
 * 쓰기 + 쓰기 흐름 안의 읽기(고칠 행 로드·잠금·저장 전 count). Service 가 주입. 읽기 전용 조회는 {@link UserQueryRepository}.
 * 단건 변경은 {@link #findById}/잠금으로 받은 관리 엔티티를 Service 가 {@link User} 변경 메서드로 고친다(커밋 때 변경 감지 — W7). 남은 UPDATE 메서드는 유지 예외.
 */
public interface UserRepository {

    /** SEAT 좌석 소비 직렬화 — 테넌트 행 잠금(호출부 트랜잭션 필수). 좌석 메서드가 스스로 부르며, 사용자 행을 먼저 잠그는 흐름은 그 전에 직접 부른다(교착 방지) */
    void lockTenantForSeat(int tenantSeq);

    /** 시트 검사 INSERT…SELECT(isSeat 면 테넌트 행 잠금 뒤). 삽입되면 identity PK 를 {@link User#assignUserSeq} 로 넣고 true, 시트 초과로 0건이면 false. */
    boolean insertUserWithSeatCheck(User user, boolean isSeat, int constrainValue);
    /** 건별 {@link #insertUserWithSeatCheck}. 하나라도 0건이면 INSERT_FAIL. */
    int insertAll(List<User> entities, boolean isSeat, int constrainValue);

    /** 고칠 행 로드(관리 엔티티), 없으면 null */
    User findById(int userSeq);
    /** 행 잠금(관리 엔티티) — 그 행의 첫 로드에서, 없으면 null */
    User lockUser(int userSeq);
    /** 행 잠금(관리 엔티티 목록) — 없는 seq 는 빠진다 */
    List<User> lockUsers(List<Integer> userSeqList);

    /** 시트 검사 활성화. ACTIVE 가 아니면 ACTIVE 로, isSeat 면 테넌트 행 잠금 뒤 ACTIVE 수 < constrainValue 일 때만. 0건 = 없음·이미 ACTIVE·시트 초과 */
    int activateUser(Integer loginUserSeq, int userSeq, int tenantSeq, boolean isSeat, int constrainValue);

    /** 원자적 증감(delta 음수 = 감소) */
    int addUseQuotaByte(int userSeq, Long delta);
    void increaseLoginFailureCount(Integer userSeq);
    void resetLoginFailureCount(Integer userSeq);
    int markUsersAsDeleted(UserDeleteMarkQuery query);
    /** 관리자 일괄 수정 배치 UPDATE(행마다 값이 다름) — 처리 건수 = 항목 수, 실패 시 UPDATE_FAIL */
    int updateAll(List<AdminUserBulkUpdateQuery> queries);

    int setupMfaTotp(UserMfaQuery query);
    int confirmMfaTotp(UserMfaQuery query);
    int clearPendingMfaTotp(UserMfaQuery query);
}
