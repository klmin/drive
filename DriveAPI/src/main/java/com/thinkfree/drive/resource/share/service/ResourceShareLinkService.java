package com.thinkfree.drive.resource.share.service;

public interface ResourceShareLinkService {

    /**
     * 리소스의 공유 링크 URL 을 반환한다. 토큰은 리소스 생성 시 발급되지만,
     * 토큰이 없는 레거시 리소스를 대비해 없으면 발급(get-or-create)한다.
     * 공유 등록 여부와 무관하게 동작한다(링크는 고정 주소, 접근 권한은 접근 시점에 공유로 판정).
     *
     * @param ownerUserSeq 리소스 소유자 user_seq
     * @param resourceSeq  리소스 seq
     * @return 공유 링크 URL
     */
    String getOrCreateLinkUrl(int ownerUserSeq, long resourceSeq);
}
