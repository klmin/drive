package com.thinkfree.drive.permission.support;

/**
 * 권한 검증 대상이 되는 "요청 주체".
 *
 * 익명 여부의 출처는 진입점마다 다르다(아스펙트=SecurityContext, 공유링크 접근=CustomUserDetails,
 * WebOffice=오피스 토큰 등). 따라서 권한 서비스가 SecurityContext 를 직접 읽지 않고,
 * 각 호출부가 자신이 아는 정보로 이 객체를 구성해 전달한다.
 *
 * 향후 요청자 속성(외부 API 여부, 테넌트 경계 등)이 필요해지면 필드만 추가한다.
 */
public record RequestActor(int userSeq, boolean anonymous) {

    /** 일반(비익명) 요청자. */
    public static RequestActor user(int userSeq) {
        return new RequestActor(userSeq, false);
    }

    public static RequestActor of(int userSeq, boolean anonymous) {
        return new RequestActor(userSeq, anonymous);
    }
}
