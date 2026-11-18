package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_api_key 쓰기 — 전부 JPA(JdbcClient 0). 읽기는 {@link DefaultApiKeyQueryRepository}.
 * <ul>
 *   <li>{@code insert} = {@link ApiKeyJpaRepository#save}(IDENTITY 라 호출 순간 INSERT·PK 주입, 감사는 Spring Data Auditing)</li>
 *   <li>{@code findById} = {@code findById} 관리 엔티티 — 조작(만료일·상태·재발급)은 Service 가 범위를 판정한 뒤 {@code ApiKey} 변경 메서드로 고치고
 *       커밋 때 변경 감지(W5 — 구 조작 9종의 로드 → 변경 → flush → detach 삭제). 수정 감사는 Auditing, {@code @DynamicUpdate} 라 바뀐 컬럼만</li>
 *   <li>IN 삭제·last_used_date 는 JPQL {@code @Modifying}(유지 예외 — 사유는 {@link ApiKeyJpaRepository})</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultApiKeyRepository implements ApiKeyRepository {

    private final ApiKeyJpaRepository apiKeyJpaRepository;

    @Override
    public void insert(ApiKey apiKey) {
        apiKeyJpaRepository.save(apiKey);
    }

    @Override
    public ApiKey findById(int apiKeySeq) {
        return apiKeyJpaRepository.findById(apiKeySeq).orElse(null);
    }

    /** 빈 목록은 {@code IN ()} 를 만들지 않도록 0(MyBatis 때와 동일). */

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return apiKeyJpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public void updateLastUsedDate(String apiKey) {
        apiKeyJpaRepository.updateLastUsedDate(apiKey, JdbcAudit.now());
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return apiKeyJpaRepository.deleteByTenantSeq(tenantSeq);
    }
}
