package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.dto.ApiKeySaveDto;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;

import java.time.Instant;
import java.util.List;

/**
 * 조작 3종(만료일 수정·상태 변경·재발급)은 대상 범위별로 나뉜다.
 * ByTenant=관리자(자기 테넌트의 ADMIN·USER role 키), ByRegisterUser=사용자(본인이 발급한 키),
 * ForSuperAdmin=슈퍼관리자(테넌트·role 무관 전체). 각 목록 조회 범위와 같다.
 */
public interface ApiKeyService {

    String save(ApiKeySaveDto dto);

    void updateExpireDateByTenant(int apiKeySeq, Integer tenantSeq, Instant expireDate);
    void updateExpireDateByRegisterUser(int apiKeySeq, Integer registerUserSeq, Instant expireDate);
    void updateExpireDateForSuperAdmin(int apiKeySeq, Instant expireDate);

    /** 보호 키 설정·해제 — 슈퍼관리자 경로 전용 */
    void updateProtectionForSuperAdmin(int apiKeySeq, boolean isProtected);

    void updateStatusByTenant(int apiKeySeq, Integer tenantSeq, ApiKeyStatus status);
    void updateStatusByRegisterUser(int apiKeySeq, Integer registerUserSeq, ApiKeyStatus status);
    void updateStatusForSuperAdmin(int apiKeySeq, ApiKeyStatus status);

    /** 계정 완전 삭제 cascade — 대상 사용자들이 소유한 API Key 행을 지운다(다른 cascade와 동일, 0건이어도 정상). INACTIVE로 남기면 관리자 메뉴에서 재활성화될 수 있어 행 삭제로 확정(09-09) */
    void deleteByUsers(List<Integer> userSeqList);

    /** @return 새로 발급된 키 원문 — 이 응답에서만 볼 수 있다 */
    String reissueByTenant(int apiKeySeq, Integer tenantSeq);
    String reissueByRegisterUser(int apiKeySeq, Integer registerUserSeq);
    String reissueForSuperAdmin(int apiKeySeq);

    void updateLastUsedDate(String apiKey);
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    void deleteByTenant(Integer tenantSeq);
}
