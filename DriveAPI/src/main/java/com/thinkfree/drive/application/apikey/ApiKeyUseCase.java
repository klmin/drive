package com.thinkfree.drive.application.apikey;

import com.thinkfree.drive.application.apikey.command.ApiKeyExpireDateUpdateCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.application.apikey.converter.ApiKeyCommandConverter;
import com.thinkfree.drive.application.apikey.converter.ApiKeyInfoConverter;
import com.thinkfree.drive.application.apikey.info.ApiKeyListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyIssuer;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 진입점(내부 API·외부 API·MCP) 공용 API Key usecase.
 * 입출력은 application 소유의 Command/{@link ApiKeyScope} 만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 *
 * <p>경로별로 다른 것은 조작 범위 하나뿐이라 조작마다 메서드는 하나다 — 범위는 어댑터가 {@link ApiKeyScope} 로 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyUseCase {

    private final ApiKeyService apiKeyService;
    private final ApiKeyFindService apiKeyFindService;
    private final ApiKeyIssuer apiKeyIssuer;
    private final ApiKeyCommandConverter apiKeyCommandConverter;
    private final ApiKeyInfoConverter apiKeyInfoConverter;
    private final TenantPolicy tenantPolicy;

    /**
     * 조회 범위는 조작 범위와 같다 — 여기 보이는 키가 곧 조작 가능한 키다.
     * 슈퍼관리자만 {@code command.tenantSeq} 로 테넌트를 좁힐 수 있다.
     */
    public CursorPageInfo<ApiKeyListInfo> findAllPaginated(ApiKeyListCommand command, ApiKeyScope scope) {
        var apiKeys = switch (scope.type()) {
            case TENANT -> apiKeyFindService.findAll(
                    apiKeyCommandConverter.toListQuery(command, scope.tenantSeq(), command.registerUserSeq()));
            case REGISTER_USER -> apiKeyFindService.findAllByRegisterUser(
                    apiKeyCommandConverter.toListQuery(command, null, scope.registerUserSeq()));
            case ALL -> apiKeyFindService.findAllForSuperAdmin(
                    apiKeyCommandConverter.toListQuery(command, command.tenantSeq(), command.registerUserSeq()));
        };

        return CursorPageInfo.of(apiKeyInfoConverter.toListInfos(apiKeys, hidesKeyValue(scope, apiKeys)), apiKeys,
                command.pageSize(), null, ApiKeyData::getApiKeySeq, null);
    }

    /** 관리자·사용자 경로에서 테넌트가 키 값 보기를 허용하지 않으면 true — 범위 안 키는 모두 한 테넌트 소속이다 */
    private boolean hidesKeyValue(ApiKeyScope scope, List<ApiKeyData> apiKeys) {
        if (scope.type() == ApiKeyScope.Type.ALL || apiKeys.isEmpty()) {
            return false;
        }
        Integer tenantSeq = scope.tenantSeq() != null ? scope.tenantSeq() : apiKeys.get(0).getTenantSeq();
        return !tenantPolicy.allows(tenantSeq, TenantFeature.API_KEY_INFO_VIEW);
    }

    /**
     * 키의 role·tenantSeq 는 요청으로 받지 않고 {@link ApiKeyIssuer} 가 대상 계정을 조회해 채운다.
     *
     * @return 발급된 키 원문 — 이 응답에서만 볼 수 있다
     */
    public String issue(ApiKeyIssueCommand command, ApiKeyActor actor) {
        // 관리자·사용자 발급 키는 로그인 사용자의 테넌트에 속한다(ApiKeyIssuer) — SYSTEM_MANAGED + SINGLE 이면 슈퍼관리자만 발급
        if (actor.roleType() != RoleType.SUPER_ADMIN) {
            tenantPolicy.require(actor.tenantSeq(), TenantFeature.API_KEY_MANAGE);
        }
        var issueDto = apiKeyCommandConverter.toIssueDto(command, actor);
        var saveDto = apiKeyIssuer.resolve(issueDto);
        return apiKeyService.save(saveDto);
    }

    /** 만료된 키도 미래 날짜로 연장하면 다시 쓸 수 있다 — 만료 판정이 조회 시점 NOW() 비교라 즉시 복구된다 */
    public void updateExpireDate(ApiKeyExpireDateUpdateCommand command, ApiKeyScope scope) {
        switch (scope.type()) {
            case TENANT -> apiKeyService.updateExpireDateByTenant(
                    command.apiKeySeq(), scope.tenantSeq(), command.expireDate());
            case REGISTER_USER -> apiKeyService.updateExpireDateByRegisterUser(
                    command.apiKeySeq(), scope.registerUserSeq(), command.expireDate());
            case ALL -> apiKeyService.updateExpireDateForSuperAdmin(
                    command.apiKeySeq(), command.expireDate());
        }
    }

    /** 보호 키 설정·해제 — 슈퍼관리자 경로 전용(테넌트 무관 전체 키). 해제하면 관리자·사용자 경로에서도 이 키를 바꿀 수 있다 */
    public void updateProtectionForSuperAdmin(int apiKeySeq, boolean isProtected) {
        apiKeyService.updateProtectionForSuperAdmin(apiKeySeq, isProtected);
    }

    public void activate(int apiKeySeq, ApiKeyScope scope) {
        updateStatus(apiKeySeq, ApiKeyStatus.ACTIVE, scope);
    }

    public void deactivate(int apiKeySeq, ApiKeyScope scope) {
        updateStatus(apiKeySeq, ApiKeyStatus.INACTIVE, scope);
    }

    private void updateStatus(int apiKeySeq, ApiKeyStatus status, ApiKeyScope scope) {
        switch (scope.type()) {
            case TENANT -> apiKeyService.updateStatusByTenant(apiKeySeq, scope.tenantSeq(), status);
            case REGISTER_USER -> apiKeyService.updateStatusByRegisterUser(apiKeySeq, scope.registerUserSeq(), status);
            case ALL -> apiKeyService.updateStatusForSuperAdmin(apiKeySeq, status);
        }
    }

    /**
     * 키 문자열을 새로 만들고 상태를 ACTIVE 로 되돌린다. 이전 키는 즉시 무효가 된다.
     *
     * @return 새 키 원문 — 이 응답에서만 볼 수 있다
     */
    public String reissue(int apiKeySeq, ApiKeyScope scope) {
        return switch (scope.type()) {
            case TENANT -> apiKeyService.reissueByTenant(apiKeySeq, scope.tenantSeq());
            case REGISTER_USER -> apiKeyService.reissueByRegisterUser(apiKeySeq, scope.registerUserSeq());
            case ALL -> apiKeyService.reissueForSuperAdmin(apiKeySeq);
        };
    }
}
