package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.apikey.ApiKeyActor;
import com.thinkfree.drive.application.apikey.ApiKeyScope;
import com.thinkfree.drive.application.apikey.ApiKeyUseCase;
import com.thinkfree.drive.application.apikey.command.ApiKeyExpireDateUpdateCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.mcp.security.McpToolTenantApiKey;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.AdminApiKeyToolConverter;
import com.thinkfree.drive.mcp.tool.response.AdminApiKeyListToolResponse;
import com.thinkfree.drive.mcp.tool.response.ApiKeyCreateToolResponse;
import com.thinkfree.drive.mcp.tool.response.ApiKeyReissueToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 관리자(ADMIN) 전용 API Key MCP 툴 — application.apikey의 공용 usecase를
 * 내부 관리자 API(AdminApiKeyController)와 동일한 범위({@link ApiKeyScope#tenant})로 호출한다.
 * 클래스 @McpToolRole(ADMIN)이 웹의 URL role 게이트(/api/v1/admins/** = ADMIN)와 동형 접근 제어를 담당하며,
 * role 어스펙트는 정확 일치 모델이라 USER·SUPER_ADMIN 계정은 인밴드 거부된다.
 * 조회 범위의 테넌트는 웹 관리자 경로와 동일하게 TenantContext(Host 기준)에서 얻는다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.ADMIN)
public class AdminApiKeyTools {

    /** 키 변경 툴 4개(재발급·활성화·비활성화·만료일 변경) 공통 거부 조건 — 판정은 ApiKeyUseCase(웹·외부 API 와 같음) */
    private static final String PROTECTED_KEY_RESTRICTION =
            "보호 키(drive_admin_list_api_keys 의 isProtected=true — 슈퍼관리자가 보호 키로 지정한 키(발급 pt=true 또는 보호 설정 API))는 이 툴로 바꿀 수 없다(API_KEY_017). "
            + "시스템 관리형 단일 사용자 테넌트(SYSTEM_MANAGED + SINGLE)에서는 보호 여부와 관계없이 모든 키가 거부된다(TENANT_011 — 키 관리는 슈퍼관리자 전용). ";

    private final ApiKeyUseCase apiKeyUseCase;
    private final AdminApiKeyToolConverter adminApiKeyToolConverter;

    @Tool(name = "drive_admin_list_api_keys",
            description = "관리자 전용 — 조직(테넌트)에 발급된 API Key 목록을 조회한다 (웹 관리자 화면의 API Key 목록과 동일 데이터). "
                    + "'발급된 API 키 보여줘', '만료된 키 있어?', '이 사용자 키 뭐 있어?'처럼 키 발급 현황이 필요할 때 사용한다. "
                    + "자기 테넌트의 관리자·사용자 대상 키가 모두 나오며, 항목에는 apiKeySeq·keyName·keyPurpose·"
                    + "maskedApiKey(전체 마스킹 — 키 원문은 어디서도 다시 볼 수 없다)·대상 계정(userSeq/userId/userName/roleType)·"
                    + "scopes·status(ACTIVE/INACTIVE)·expireDate·isExpired(만료 여부 — 만료 키도 목록에 나온다)·"
                    + "lastUsedDate·registerDate·isProtected(보호 키 여부 — 슈퍼관리자가 보호 키로 지정한 키(발급 pt=true 또는 보호 설정 API)로, 관리자 툴로는 바꿀 수 없다)가 포함된다. "
                    + "결과는 커서 기반 페이지네이션(apiKeySeq 내림차순 — 최근 발급 키부터)이며, 응답 hasNext가 true면 "
                    + "nextCursor를 다음 호출의 cursorId로 그대로 넘기면 다음 페이지를 받는다. "
                    + "이 툴은 조회 전용이며 키 발급/활성화/만료일 변경은 하지 않는다.")
    public McpToolResult<AdminApiKeyListToolResponse> listApiKeys(
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 — 직전 응답의 nextCursor (마지막 항목의 apiKeySeq). 첫 페이지면 생략", required = false) Long cursorId) {

        log.info("[MCP] drive_admin_list_api_keys called. pageSize : {}, cursorId : {}", pageSize, cursorId);

        return McpToolResult.executeAuthed(details -> {

            var command = ApiKeyListCommand.builder()
                    .cursorId(cursorId)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            return adminApiKeyToolConverter.toListResponse(
                    apiKeyUseCase.findAllPaginated(command, ApiKeyScope.tenant(TenantContext.getOrThrow())));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_create_api_key",
            description = "관리자 전용 — 새 API Key를 발급한다 (웹 관리자 화면의 API Key 발급과 동일 동작). "
                    + "'API 키 발급해줘', '이 사용자한테 읽기 전용 키 만들어줘'처럼 새 키가 필요할 때, "
                    + "사용자가 발급을 명시적으로 요청한 경우에만 사용한다. "
                    + "대상 계정(userSeq)을 지정하지 않으면 로그인한 관리자 본인의 키가 발급되고, "
                    + "본인 키는 관리자(ADMIN) 키다. 지정하면 자기 테넌트에서 사용자(USER) 역할을 가진 계정의 USER 키가 발급된다 — "
                    + "사용자 역할 없이 관리자 역할만 가진 다른 계정은 대상으로 지정할 수 없고(API_KEY_012), "
                    + "타테넌트/존재하지 않는 계정은 '유저 정보를 찾을 수 없음' 에러를 반환한다. "
                    + "키의 role 은 요청으로 정할 수 없다 — 본인이면 ADMIN, 다른 계정이면 USER 로 정해진다. "
                    + "시스템 관리형 단일 사용자 테넌트(SYSTEM_MANAGED + SINGLE)에서는 키 발급이 슈퍼관리자 전용이라 거부된다(TENANT_011). "
                    + "scopes로 허용되는 조합은 정확히 2가지뿐이다 — 읽기 전용 [\"api:read\",\"mcp:read\"] 또는 "
                    + "읽기+쓰기 [\"api:read\",\"api:write\",\"mcp:read\",\"mcp:write\"] — 그 외 조합·부분 지정은 에러다. "
                    + "expireDate는 날짜(yyyy-MM-dd)만 받으며 그 날 23:59:59에 만료된다 — 오늘 이후 날짜만 허용(무기한 불가). "
                    + "⚠️ 응답의 apiKey가 키 원문이며 이 응답에서 단 한 번만 노출된다 — 목록 조회는 마스킹 값만 보여주므로 "
                    + "사용자에게 지금 바로 안전한 곳에 보관하라고 안내한다. 키 원문은 대화 기록에 평문으로 남으므로 "
                    + "유출이 의심되면 drive_admin_deactivate_api_key로 정지하거나 웹에서 재발급한다. "
                    + "⚠️ 이 툴로 mcp:write 권한 키를 새로 만들 수 있다(권한 자기증식) — 사용자 요청 없이 임의로 발급하지 않는다.")
    public McpToolResult<ApiKeyCreateToolResponse> createApiKey(
            @ToolParam(description = "키 이름") String keyName,
            @ToolParam(description = "키 용도 설명") String keyPurpose,
            @ToolParam(description = "만료일 — yyyy-MM-dd(그 날 23:59:59에 만료). 오늘 이후 날짜만 허용") String expireDate,
            @ToolParam(description = "부여할 scope 배열 — [\"api:read\",\"mcp:read\"](읽기 전용) 또는 [\"api:read\",\"api:write\",\"mcp:read\",\"mcp:write\"](읽기+쓰기)만 허용") List<String> scopes,
            @ToolParam(description = "대상 계정 userSeq — 생략하면 로그인 관리자 본인. 지정 시 자기 테넌트에서 사용자(USER) 역할을 가진 계정만 가능(그 계정의 USER 키)", required = false) Integer userSeq) {

        log.info("[MCP] drive_admin_create_api_key called. keyName : {}, keyPurpose : {}, expireDate : {}, scopes : {}, userSeq : {}",
                keyName, keyPurpose, expireDate, scopes, userSeq);

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.requireText(keyName, "keyName");
            McpToolParams.requireText(keyPurpose, "keyPurpose");
            McpToolParams.requireText(expireDate, "expireDate");
            var expireAt = DriveTime.endOfDay(McpToolParams.parseDate(expireDate, "expireDate"));
            McpToolParams.requireFuture(expireAt, expireDate, "expireDate");

            // scopes 필수·허용 조합, 대상 계정 검증(테넌트·role 상한)은 발급 경로 공통 규칙이라 ApiKeyIssuer 소유 — 인밴드 변환됨.
            // 웹 어댑터의 슈퍼관리자 차단(API_KEY_SUPER_ADMIN_PATH_REQUIRED)은 클래스 @McpToolRole(ADMIN) 정확 일치가 대신한다.
            var command = ApiKeyIssueCommand.builder()
                    .keyName(keyName)
                    .keyPurpose(keyPurpose)
                    .expireDate(expireAt)
                    .scopes(scopes)
                    .targetUserSeq(userSeq)
                    .build();

            return new ApiKeyCreateToolResponse(apiKeyUseCase.issue(command, ApiKeyActor.of(details)));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_reissue_api_key",
            description = "관리자 전용 — 기존 API Key의 키 문자열을 새로 발급한다 (웹 관리자 화면의 API Key 재발급 버튼과 동일 동작). "
                    + "'이 키 재발급해줘', '키가 유출된 것 같으니 갈아줘'처럼 키 원문만 교체하고 이름·용도·scope·대상 계정은 "
                    + "그대로 유지할 때, 사용자가 재발급을 명시적으로 요청한 경우에만 사용한다. "
                    + "대상 apiKeySeq는 drive_admin_list_api_keys로 먼저 조회해 얻는다. "
                    + "⚠️ 기존 키는 즉시 무효가 된다 — 그 키를 쓰던 모든 연결이 다음 요청부터 401로 끊기며, "
                    + "지금 이 연결이 인증에 쓰고 있는 키를 재발급하면 이 연결 자체가 끊긴다. 자기 연결 키인지 반드시 확인 후 호출한다. "
                    + "재발급하면 status는 ACTIVE로 되돌아간다(INACTIVE 키도 재발급 즉시 사용 가능 상태가 된다). "
                    + "단 만료일은 바뀌지 않는다 — 만료(isExpired=true)된 키는 재발급해도 인증 불가이며, "
                    + "연장은 drive_admin_update_api_key_expire_date를 사용한다. "
                    + "⚠️ 응답의 apiKey가 새 키 원문이며 이 응답에서 단 한 번만 노출된다 — 목록 조회는 마스킹 값만 보여주므로 "
                    + "사용자에게 지금 바로 안전한 곳에 보관하라고 안내한다. 키 원문은 대화 기록에 평문으로 남으므로 "
                    + "유출이 의심되면 drive_admin_deactivate_api_key로 정지하거나 다시 재발급한다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 apiKeySeq면 'API Key를 찾을 수 없음' 에러를 반환한다. "
                    + PROTECTED_KEY_RESTRICTION)
    public McpToolResult<ApiKeyReissueToolResponse> reissueApiKey(
            @McpToolTenantApiKey @ToolParam(description = "재발급할 API Key 식별자(apiKeySeq). drive_admin_list_api_keys 응답의 apiKeySeq") Integer apiKeySeq) {

        log.info("[MCP] drive_admin_reissue_api_key called. apiKeySeq : {}", apiKeySeq);

        return McpToolResult.executeAuthed(details -> {
            // @McpToolTenantApiKey 어스펙트는 null 파라미터를 건너뛰므로(TenantBoundarySupport) null 가드가 아니다 — 본문 검증 필요.
            McpToolParams.require(apiKeySeq, "apiKeySeq");

            return new ApiKeyReissueToolResponse(
                    apiKeyUseCase.reissue(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow())));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_activate_api_key",
            description = "관리자 전용 — 비활성(INACTIVE) 상태의 API Key를 활성(ACTIVE)으로 전환한다 (웹 관리자 화면의 API Key 활성화 버튼과 동일 동작). "
                    + "'이 API 키 다시 켜줘', '정지시킨 키 활성화해줘'처럼 사용 중지된 키를 되살릴 때 사용한다. "
                    + "대상 apiKeySeq는 drive_admin_list_api_keys로 먼저 조회해 얻는다. "
                    + "이미 활성(ACTIVE) 상태인 키에 호출해도 에러 없이 성공한다(멱등). "
                    + "주의 — 활성화 상태와 만료는 별개다: status가 ACTIVE라도 만료(isExpired=true)된 키는 인증에 쓸 수 없으며, "
                    + "만료된 키를 되살리려면 활성화가 아니라 만료일 연장(drive_admin_update_api_key_expire_date)이 필요하다. "
                    + "변경은 즉시 반영된다 — 활성화 직후 요청부터 그 키로 인증할 수 있다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 apiKeySeq면 'API Key를 찾을 수 없음' 에러를 반환한다. "
                    + PROTECTED_KEY_RESTRICTION
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음). "
                    + "비활성화는 drive_admin_deactivate_api_key를 사용한다.")
    public McpToolResult<Void> activateApiKey(
            @McpToolTenantApiKey @ToolParam(description = "활성화할 API Key 식별자(apiKeySeq). drive_admin_list_api_keys 응답의 apiKeySeq") Integer apiKeySeq) {

        log.info("[MCP] drive_admin_activate_api_key called. apiKeySeq : {}", apiKeySeq);

        return McpToolResult.executeAuthed(details -> {
            apiKeyUseCase.activate(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow()));
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_deactivate_api_key",
            description = "관리자 전용 — 활성(ACTIVE) 상태의 API Key를 비활성(INACTIVE)으로 전환한다 (웹 관리자 화면의 API Key 비활성화 버튼과 동일 동작). "
                    + "'이 API 키 사용 중지해줘', '이 키 잠가줘'처럼 키 사용을 즉시 막을 때 사용한다. "
                    + "대상 apiKeySeq는 drive_admin_list_api_keys로 먼저 조회해 얻는다. "
                    + "이미 비활성(INACTIVE) 상태인 키에 호출해도 에러 없이 성공한다(멱등). "
                    + "변경은 즉시 반영된다 — 비활성화 직후 요청부터 그 키 인증이 401로 거부된다. "
                    + "⚠️ 지금 이 연결이 인증에 쓰고 있는 키를 비활성화하면 다음 요청부터 이 연결 자체가 401로 끊긴다 — "
                    + "자기 연결 키인지 반드시 확인 후 호출한다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 apiKeySeq면 'API Key를 찾을 수 없음' 에러를 반환한다. "
                    + PROTECTED_KEY_RESTRICTION
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음). "
                    + "되살리려면 drive_admin_activate_api_key를 사용한다"
                    + "(단 만료된 키는 활성화해도 인증 불가 — 만료일 연장은 drive_admin_update_api_key_expire_date).")
    public McpToolResult<Void> deactivateApiKey(
            @McpToolTenantApiKey @ToolParam(description = "비활성화할 API Key 식별자(apiKeySeq). drive_admin_list_api_keys 응답의 apiKeySeq") Integer apiKeySeq) {

        log.info("[MCP] drive_admin_deactivate_api_key called. apiKeySeq : {}", apiKeySeq);

        return McpToolResult.executeAuthed(details -> {
            apiKeyUseCase.deactivate(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow()));

            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_update_api_key_expire_date",
            description = "관리자 전용 — API Key의 만료일을 변경한다 (웹 관리자 화면의 만료일 수정과 동일 동작). "
                    + "'이 키 만료일 연장해줘', '만료된 키 다시 쓸 수 있게 해줘'처럼 키의 사용 기한을 바꿀 때 사용한다. "
                    + "대상 apiKeySeq는 drive_admin_list_api_keys로 먼저 조회해 얻는다. "
                    + "이미 만료(isExpired=true)된 키도 미래 날짜로 연장하면 즉시 다시 쓸 수 있다 — "
                    + "만료 판정이 조회 시점 비교라 별도 활성화 없이 복구된다(단 status가 INACTIVE면 drive_admin_activate_api_key도 필요). "
                    + "만료일은 날짜(yyyy-MM-dd)만 받으며 그 날 23:59:59에 만료된다 — 무기한(만료 없음)은 불가하고, 지난 날짜로는 바꿀 수 없다. "
                    + "변경은 즉시 반영된다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 apiKeySeq면 'API Key를 찾을 수 없음' 에러를 반환한다. "
                    + PROTECTED_KEY_RESTRICTION
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음 — 변경 결과는 drive_admin_list_api_keys로 재조회).")
    public McpToolResult<Void> updateApiKeyExpireDate(
            @McpToolTenantApiKey @ToolParam(description = "만료일을 변경할 API Key 식별자(apiKeySeq). drive_admin_list_api_keys 응답의 apiKeySeq") Integer apiKeySeq,
            @ToolParam(description = "새 만료일 — yyyy-MM-dd(그 날 23:59:59에 만료). 오늘 이후 날짜만 허용") String expireDate) {

        log.info("[MCP] drive_admin_update_api_key_expire_date called. apiKeySeq : {}, expireDate : {}", apiKeySeq, expireDate);

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.requireText(expireDate, "expireDate");
            var newExpireDate = DriveTime.endOfDay(McpToolParams.parseDate(expireDate, "expireDate"));
            McpToolParams.requireFuture(newExpireDate, expireDate, "expireDate");

            var command = ApiKeyExpireDateUpdateCommand.builder()
                    .apiKeySeq(apiKeySeq)
                    .expireDate(newExpireDate)
                    .build();
            apiKeyUseCase.updateExpireDate(command, ApiKeyScope.tenant(TenantContext.getOrThrow()));

            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

}
