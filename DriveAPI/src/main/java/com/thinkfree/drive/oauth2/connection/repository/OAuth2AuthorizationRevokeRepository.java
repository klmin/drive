package com.thinkfree.drive.oauth2.connection.repository;

import java.util.List;

/**
 * 연결 해제 시 SAS 소유 테이블의 (사용자, 클라이언트) 잔재 일괄 삭제.
 * SAS 서비스에는 사용자×클라이언트 단위 벌크 삭제 API가 없어 직접 DELETE 한다
 * (OAuth2AuthorizationCleanupScheduler와 동일 방침 — 공식 스키마 컬럼명 하드코딩).
 */
public interface OAuth2AuthorizationRevokeRepository {

    /** oauth2_authorization 삭제 — refresh token 즉시 무효화 */
    int deleteAuthorizations(String principalName, String registeredClientId);

    /** oauth2_authorization_consent 삭제 — 재연결 시 동의 화면 재노출 */
    int deleteConsents(String principalName, String registeredClientId);

    /** 계정 완전 삭제 cascade — 대상 계정들의 oauth2_authorization·consent 전부(클라이언트 무관). 지운 행 수 합, 목록이 비면 0 */
    int deleteAllByPrincipals(List<String> principalNames);
}
