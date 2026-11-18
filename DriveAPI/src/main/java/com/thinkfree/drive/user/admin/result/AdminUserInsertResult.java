package com.thinkfree.drive.user.admin.result;

import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 일괄 등록(registerAll·upsertAll 의 insert 행)으로 방금 INSERT 한 사용자 — 감사 기록·응답에 쓰는 신원 값과 부여한 역할.
 * getter 이름은 엔티티와 같다(감사 호출부가 {@code getUserSeq()/getUserId()/getUserName()} 을 읽는다). roles 는 감사 ADMIN_/USER_ 분기용
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminUserInsertResult {
    private final Integer userSeq;
    private final String userId;
    private final String userName;
    private final Set<RoleType> roles;

    public static AdminUserInsertResult from(User user, Set<RoleType> roles) {
        return new AdminUserInsertResult(user.getUserSeq(), user.getUserId(), user.getUserName(), roles);
    }
}
