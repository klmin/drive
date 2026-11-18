package com.thinkfree.drive.resource.share.service;

public interface ResourceShareTokenService {

    /**
     * 리소스 공유 링크용 고유 토큰을 생성한다. tb_resource.share_token 기준으로 중복을 검사하고
     * 충돌 시 재시도한다. (토큰의 진실 소스는 리소스이며, 링크는 리소스의 고정 주소다.)
     */
    String createNewToken();
}
