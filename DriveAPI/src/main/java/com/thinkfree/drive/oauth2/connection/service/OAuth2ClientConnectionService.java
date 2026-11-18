package com.thinkfree.drive.oauth2.connection.service;

import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;

import java.util.List;
import java.util.Set;

public interface OAuth2ClientConnectionService {

    /** 토큰 발급/갱신 시 연결 기록 — ConnectionRecordingAuthorizationService(발급 훅)가 호출 */
    void recordIssuance(int userSeq, String clientId, Set<String> scopes);

    /**
     * 연결 존재 확인 + 마지막 사용일 스로틀 기록 — RS 필터가 매 요청 호출.
     * false = 연결 없음(해제됨) → 호출부가 인증 거부. 기발급 access 토큰의 즉시 무효화 지점.
     */
    boolean verifyAndTouch(int userSeq, String clientId);

    List<OAuth2ClientConnectionData> findConnections(int userSeq);

    /** 연결 해제 — connection 행 + SAS 인가·동의 행 일괄 삭제 (본인 소유 검증 포함) */
    void disconnect(int userSeq, long connectionSeq);
    /** 계정 완전 삭제 cascade — 대상 계정들의 연결 행과 SAS 인가·동의(oauth2_authorization·consent, principal = userSeq) 전부 */
    void deleteByUsers(List<Integer> userSeqList);
}
