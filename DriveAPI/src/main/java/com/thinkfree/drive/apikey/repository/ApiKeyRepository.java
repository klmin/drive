package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.apikey.entity.ApiKey;

import java.time.Instant;
import java.util.List;

/** 쓰기(Service 가 주입) + 쓰기에 묶인 조회(고칠 행 로드). 읽기는 {@link ApiKeyQueryRepository}. */
public interface ApiKeyRepository {

    /** JPA save — IDENTITY 라 호출 순간 INSERT, identity PK 가 엔티티에 채워진다(감사 컬럼은 Spring Data Auditing). */
    void insert(ApiKey apiKey);

    /**
     * 고칠 키 로드(관리 엔티티) — Service 가 범위를 판정하고 변경 메서드로 고친다(커밋 때 변경 감지). 없으면 null.
     * 화면·판단용 조회에는 쓰지 않는다({@link ApiKeyQueryRepository}).
     */
    ApiKey findById(int apiKeySeq);

    int deleteByUserSeqList(List<Integer> userSeqList);

    /** last_used_date = 현재 시각(초 절삭). 마지막 사용일이 staleBefore 이후면 쓰지 않는다. 쓴 행 수(0 또는 1) */
    int updateLastUsedDate(String apiKey, Instant staleBefore);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}
