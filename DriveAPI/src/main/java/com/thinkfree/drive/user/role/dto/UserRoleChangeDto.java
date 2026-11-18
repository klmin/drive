package com.thinkfree.drive.user.role.dto;

import com.thinkfree.drive.user.enums.RoleType;

import java.util.Set;

/**
 * 기존 계정의 역할 교체 입력 — 요청 역할 전부로 바꾼다(부여·회수는 현재 보유 역할과의 차이).
 *
 * @param userSeq      대상 계정
 * @param roles        교체 후 보유할 역할 전부
 * @param actorUserSeq 요청자 — 본인 ADMIN 회수 판정, 부여 행의 등록자(시큐리티 사용자가 있으면 그 사용자로 덮어쓴다)
 * @param useQuotaByte 대상 계정의 사용량 — USER 회수 판정. 호출부가 잠근 사용자 행의 값을 넘긴다
 */
public record UserRoleChangeDto(Integer userSeq, Set<RoleType> roles, Integer actorUserSeq, long useQuotaByte) {
}
