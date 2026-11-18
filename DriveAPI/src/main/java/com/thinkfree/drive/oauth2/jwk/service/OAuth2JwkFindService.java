package com.thinkfree.drive.oauth2.jwk.service;

import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;

import java.util.List;

public interface OAuth2JwkFindService {

    /** 유효한 키 전부 — 최신 우선 정렬(register_date DESC, kid DESC) */
    List<OAuth2JwkData> findAllValid();
}
