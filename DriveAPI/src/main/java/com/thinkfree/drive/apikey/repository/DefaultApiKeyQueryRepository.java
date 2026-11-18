package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_api_key 읽기 — 전부 JPQL 생성자 식(엔티티 로드·detach 없음, W5): PK 단건(findById — 판정은 호출부 Java)·인증은 JPQL 생성자 식({@link ApiKeyJpaRepository}), 조인·커서 목록 3종도 JPQL 생성자 식(5차 패스 C — 구 ApiKeyMapper.xml 의 조회 6 statement, JdbcClient 0). 쓰기는 {@link DefaultApiKeyRepository}.
 * <p>
 * 목록 3종은 조작 범위와 1:1 — findAll(관리자: 자기 테넌트의 ADMIN·USER 키) / findAllByRegisterUser(사용자: 본인 발급 키) / findAllForSuperAdmin(전체).
 * 만료 판정은 status 가 아니라 조회 시점 DB 시계 비교 — 목록·인증 모두 JPQL {@code instant}(PG CURRENT_TIMESTAMP)(만료 배치 없음, 연장 시 즉시 부활).
 */
@Repository
@RequiredArgsConstructor
public class DefaultApiKeyQueryRepository implements ApiKeyQueryRepository {

    /** 관리자 목록에 보이는 키 role — 자기 테넌트의 ADMIN·USER(슈퍼관리자 키 제외) */
    private static final List<RoleType> ADMIN_LIST_ROLES = List.of(RoleType.ADMIN, RoleType.USER);

    private final ApiKeyJpaRepository jpaRepository;

    @Override
    public List<ApiKeyData> findAll(ApiKeyListQuery query) {
        var page = query.getPage();
        return jpaRepository.findListByTenant(query.getTenantSeq(), ADMIN_LIST_ROLES, query.getRegisterUserSeq(),
                cursor(page.getCursorId()), limit(page.getPageSize()));
    }

    @Override
    public List<ApiKeyData> findAllByRegisterUser(ApiKeyListQuery query) {
        var page = query.getPage();
        return jpaRepository.findListByRegisterUser(query.getRegisterUserSeq(), cursor(page.getCursorId()), limit(page.getPageSize()));
    }

    /** tenantSeq·registerUserSeq 를 주면 그 조건으로 좁힌다(없으면 전체). */
    @Override
    public List<ApiKeyData> findAllForSuperAdmin(ApiKeyListQuery query) {
        var page = query.getPage();
        return jpaRepository.findListForSuperAdmin(query.getTenantSeq(), query.getRegisterUserSeq(),
                cursor(page.getCursorId()), limit(page.getPageSize()));
    }

    /** pageSize null 은 제한 없음(구 FETCH FIRST NULL 과 같음) */
    private static Limit limit(Integer pageSize) {
        return pageSize == null ? Limit.unlimited() : Limit.of(pageSize);
    }

    private static Integer cursor(Long cursorId) {
        return cursorId == null ? null : Math.toIntExact(cursorId);
    }

    @Override
    public ApiKeyData findById(int apiKeySeq) {
        return jpaRepository.findDataById(apiKeySeq).orElse(null);
    }

    /**
     * 인증용 조회. 슈퍼관리자 키는 tenant_seq 가 NULL 이라 호스트로 못 거르므로 role_type 으로 판단한다
     * (데이터가 잘못돼 일반 키의 테넌트가 비어도 아무 호스트에서나 인증되지 않도록).
     */
    @Override
    public ApiKeyData findActiveByApiKey(String apiKey, Integer tenantSeq) {
        return jpaRepository.findActiveData(apiKey, tenantSeq, RoleType.SUPER_ADMIN, ApiKeyStatus.ACTIVE).orElse(null);
    }

}
