package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.converter.ApiKeyConverter;
import com.thinkfree.drive.apikey.dto.ApiKeySaveDto;
import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.repository.ApiKeyRepository;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.Base62TokenGenerator;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultApiKeyService implements ApiKeyService {
    
    private static final int API_KEY_LENGTH = 32;

    /** 관리자 범위가 바꿀 수 있는 키 role — SUPER_ADMIN 키는 제외(EnumSet: role NULL 이면 false) */
    private static final Set<RoleType> TENANT_ROLES = EnumSet.of(RoleType.ADMIN, RoleType.USER);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyConverter apiKeyConverter;
    private final TenantPolicy tenantPolicy;

    @Override
    public String save(ApiKeySaveDto dto) {
        String rawApiKey = Base62TokenGenerator.generate(API_KEY_LENGTH);
        var entity = apiKeyConverter.toSaveEntity(dto, rawApiKey);
        apiKeyRepository.insert(entity);
        return rawApiKey;
    }

    /*
     * 조작 3종(만료일·상태·재발급) — 고칠 키를 쓰기 리포에서 관리 엔티티로 한 번 로드해 범위를 Java 로 판정하고, 변경 메서드로 고친다.
     * 저장은 커밋 때 변경 감지(수정 감사는 Auditing, @DynamicUpdate 라 바뀐 컬럼만 — 값이 같으면 UPDATE 없음). W5 — 구 리포 조작 9종 대체.
     * 대상이 아닌 키(다른 테넌트·다른 발급자·조작 불가 role)의 seq 를 넘기는 건 정상 시나리오라 "없음" 으로 다룬다 → 404.
     * 없는 키와 응답이 같아 존재 여부도 노출되지 않는다. 범위 값이 null 이면 범위 밖(구 {@code = :param} 과 같음).
     */

    @Override
    public void updateExpireDateByTenant(int apiKeySeq, Integer tenantSeq, Instant expireDate) {
        loadInTenant(apiKeySeq, tenantSeq).changeExpireDate(expireDate);
    }

    @Override
    public void updateExpireDateByRegisterUser(int apiKeySeq, Integer registerUserSeq, Instant expireDate) {
        loadInRegisterUser(apiKeySeq, registerUserSeq).changeExpireDate(expireDate);
    }

    @Override
    public void updateExpireDateForSuperAdmin(int apiKeySeq, Instant expireDate) {
        loadInAll(apiKeySeq).changeExpireDate(expireDate);
    }

    @Override
    public void updateProtectionForSuperAdmin(int apiKeySeq, boolean isProtected) {
        loadInAll(apiKeySeq).changeProtected(isProtected);
    }

    @Override
    public void updateStatusByTenant(int apiKeySeq, Integer tenantSeq, ApiKeyStatus status) {
        loadInTenant(apiKeySeq, tenantSeq).changeStatus(status);
    }

    @Override
    public void updateStatusByRegisterUser(int apiKeySeq, Integer registerUserSeq, ApiKeyStatus status) {
        loadInRegisterUser(apiKeySeq, registerUserSeq).changeStatus(status);
    }

    @Override
    public void updateStatusForSuperAdmin(int apiKeySeq, ApiKeyStatus status) {
        loadInAll(apiKeySeq).changeStatus(status);
    }

    @Override
    public void deleteByUsers(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return;
        }
        var result = apiKeyRepository.deleteByUserSeqList(userSeqList);
        log.info("[API Key 삭제(계정 삭제 cascade)] : {}건", result);
    }

    /** 재발급은 키 문자열을 바꾸면서 상태도 ACTIVE 로 되돌린다. */
    @Override
    public String reissueByTenant(int apiKeySeq, Integer tenantSeq) {
        return reissue(loadInTenant(apiKeySeq, tenantSeq));
    }

    @Override
    public String reissueByRegisterUser(int apiKeySeq, Integer registerUserSeq) {
        return reissue(loadInRegisterUser(apiKeySeq, registerUserSeq));
    }

    @Override
    public String reissueForSuperAdmin(int apiKeySeq) {
        return reissue(loadInAll(apiKeySeq));
    }

    @Override
    public void updateLastUsedDate(String apiKey) {
        apiKeyRepository.updateLastUsedDate(apiKey);
    }

    private static String reissue(ApiKey apiKey) {
        String newRawApiKey = Base62TokenGenerator.generate(API_KEY_LENGTH);
        apiKey.reissue(newRawApiKey, ApiKeyStatus.ACTIVE);
        return newRawApiKey;
    }

    /** 관리자 범위 — 자기 테넌트의 ADMIN·USER 키 */
    private ApiKey loadInTenant(int apiKeySeq, Integer tenantSeq) {
        return requireChangeable(load(apiKeySeq, k -> tenantSeq != null && tenantSeq.equals(k.getTenantSeq()) && TENANT_ROLES.contains(k.getRoleType())));
    }

    /** 사용자 범위 — 본인이 발급한 키 */
    private ApiKey loadInRegisterUser(int apiKeySeq, Integer registerUserSeq) {
        return requireChangeable(load(apiKeySeq, k -> registerUserSeq != null && registerUserSeq.equals(k.getRegisterUserSeq())));
    }

    /**
     * 관리자·사용자 범위의 조작 제한 — 키 테넌트가 SYSTEM_MANAGED + SINGLE 이면 보호 여부와 관계없이 SYSTEM_MANAGED_RESTRICTED, 그 외 보호 키면 API_KEY_PROTECTED.
     * 범위 판정 뒤에 본다 — 범위 밖 키는 이미 API_KEY_NOT_FOUND 라 다른 범위 키의 보호 여부가 오류 코드로 드러나지 않는다. 슈퍼관리자 범위는 제한 없음.
     */
    private ApiKey requireChangeable(ApiKey apiKey) {
        tenantPolicy.require(apiKey.getTenantSeq(), TenantFeature.API_KEY_MANAGE);
        DriveAssert.throwIfTrue(Boolean.TRUE.equals(apiKey.getIsProtected()), ApiKeyErrorCode.API_KEY_PROTECTED);
        return apiKey;
    }

    /** 슈퍼관리자 범위 — 전체 */
    private ApiKey loadInAll(int apiKeySeq) {
        return load(apiKeySeq, k -> true);
    }

    /** 고칠 키 1회 로드(관리 엔티티) + 범위 판정. 없거나 범위 밖이면 API_KEY_NOT_FOUND(범위 밖 엔티티는 고치지 않으므로 UPDATE 없음). */
    private ApiKey load(int apiKeySeq, Predicate<ApiKey> inScope) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeySeq);
        if (apiKey == null || !inScope.test(apiKey)) {
            throw ApiRuntimeException.type(ApiKeyErrorCode.API_KEY_NOT_FOUND);
        }
        return apiKey;
    }
    @Override
    public void deleteByTenant(Integer tenantSeq) {
        apiKeyRepository.deleteByTenant(tenantSeq);
    }
}
