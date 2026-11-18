package com.thinkfree.drive.user.role.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * tb_user_role — 사용자 보유 역할(사용자당 여러 행, PK (user_seq, role_type), FK 없음 — 계정 완전 삭제 cascade({@code DefaultUserCleanUpService})가 지운다).
 * <p>
 * 부여 = INSERT, 회수 = DELETE — 수정 컬럼이 없는 불변 행. 불변식(부여 가능 역할·단독 역할·0개 금지 등)은 역할 부여 공통 메서드가 검사하고 여기서는 하지 않는다.
 * <ul>
 *   <li>쓰기는 jOOQ({@code DefaultUserRoleRepository}) — JPA 로 persist 하지 않는다. 이 객체는 부여할 값(user_seq·role_type·선언 등록자)을 나르는 용도</li>
 *   <li>JPA 매핑은 JPQL 조회({@code UserRoleJpaRepository}·사용자 조회의 {@code HOLDS_ROLE} 보유 조건)용으로 남긴다</li>
 *   <li>등록 감사는 리포가 {@code JdbcAudit} 로 정한다 — register_user_seq 는 시큐리티 사용자 우선(없으면 {@link #grant} 에 넘긴 값)</li>
 * </ul>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_user_role")
@IdClass(UserRole.Key.class)
public class UserRole extends BaseRegisterEntity {

    @Id
    private Integer userSeq;

    @Id
    @Enumerated(EnumType.STRING)
    private RoleType roleType;

    private UserRole(Integer userSeq, RoleType roleType, Integer registerUserSeq) {
        this.userSeq = userSeq;
        this.roleType = roleType;
        assignRegisterAudit(registerUserSeq, Instant.now()); // 리포가 INSERT 때 시큐리티 사용자·현재 시각으로 다시 정한다
    }

    /** 역할 부여 행 — 등록자는 시스템 작업(시큐리티 사용자 없음)일 때만 남는다 */
    public static UserRole grant(Integer userSeq, RoleType roleType, Integer registerUserSeq) {
        return new UserRole(userSeq, roleType, registerUserSeq);
    }

    /** 복합 PK (user_seq, role_type) */
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer userSeq;
        private RoleType roleType;
    }
}
