package com.thinkfree.drive.user.data;

/**
 * 계정 식별 값(읽기 전용) — 감사 기록의 대상·행위자 인자 타입. {@link UserDetailData}·{@link UserAuthData} 가 구현한다.
 * 감사는 이 값들(seq·아이디·이름·테넌트)만 읽으므로, 조회 경로가 어떤 DTO 를 들고 있든 그대로 넘길 수 있다(10-12 — 감사 인자를 엔티티에서 분리).
 */
public interface UserIdentity {

    Integer getUserSeq();

    String getUserId();

    String getUserName();

    Integer getTenantSeq();
}
