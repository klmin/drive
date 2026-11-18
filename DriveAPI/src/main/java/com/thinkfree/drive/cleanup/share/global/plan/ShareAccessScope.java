package com.thinkfree.drive.cleanup.share.global.plan;

/**
 * 공유 접근범위 타입. 공유 1건은 이 중 하나의 scope 을 가진다.
 * <p>
 * {@code com.thinkfree.drive.share.enums.ShareType} 과 1:1 매핑된다
 * (SPECIFIC→SPECIFIC_USERS, ORG_LINK→ORG_LINK, PUBLIC→PUBLIC_LINK).
 * 단 PUBLIC_LINK 는 cleanup 선별삭제에만 쓰이고 생성/조회 flow 는 아직 미배선(Phase 2).
 */
public enum ShareAccessScope {
    /** 제한됨: 직접 추가한 공유대상만 접근. 공유대상 필수. */
    SPECIFIC,
    /** 조직 내부 공개: 링크 기반 접근. */
    ORG_LINK,
    /** 전체 공개: 익명 포함 누구나 접근. */
    PUBLIC
}
