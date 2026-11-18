package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.admin.user.AdminUserQueryUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.mcp.security.McpToolTenantUser;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.AdminUserToolConverter;
import com.thinkfree.drive.mcp.tool.response.*;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.List;

/**
 * 관리자(ADMIN) 전용 MCP 툴 — 웹 AdminController(/api/v1/admins)와 1:1 대응, application.admin.user 의 AdminUserUseCase·AdminUserQueryUseCase를
 * 내부 관리자 API와 동일하게 호출한다 (관리자 목록·사용자 상세·등록·수정·비밀번호·활성/비활성·탈퇴).
 * Tools 클래스는 웹 컨트롤러 = usecase 단위로 나눈다 — 사용자 부가 API는 {@link AdminUserTools},
 * 삭제 대기 계정은 {@link AdminPendingDeleteUserTools}, API Key는 {@link AdminApiKeyTools}.
 *
 * 클래스 @McpToolRole(ADMIN)이 웹의 URL role 게이트(/api/v1/admins/** = ADMIN)와 동형 접근 제어를 담당하며,
 * role 어스펙트는 정확 일치 모델이라 USER·SUPER_ADMIN 계정은 인밴드 거부된다.
 * 테넌트 격리는 TenantFilter가 Host 기준으로 주입한 TenantContext를 usecase가 사용 — 웹 경로와 동일.
 * 대상 userSeq를 받는 툴은 파라미터에 {@code @McpToolTenantUser}를 붙인다 — 웹의 @TenantUser 어스펙트는
 * MCP 경로에서 발화하지 않으므로 McpToolTenantAspect가 동일 검증(타테넌트·미존재 = USER_NOT_FOUND 인밴드)을 수행한다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.ADMIN)
public class AdminTools {

    private final AdminUserUseCase adminUserUseCase;
    private final AdminUserQueryUseCase adminUserQueryUseCase;
    private final AdminUserToolConverter adminUserToolConverter;

    // TB_USER.USER_NAME varchar(20). 웹은 @NotBlank만 있고 길이는 화면 maxLength가 막는다 — MCP는 화면이 없어 어댑터가 선검증(초과 시 SQL "Data too long" 예외).
    private static final int USER_NAME_MAX_LENGTH = 20;

    @Tool(name = "drive_admin_get_user",
            description = "관리자 전용 — 특정 사용자 한 명의 상세 정보를 조회한다 (웹 관리자 화면의 사용자 상세와 동일 데이터). "
                    + "'이 계정 상세 정보 보여줘', '이 사용자 할당 용량이랑 전화번호 알려줘'처럼 개별 계정의 전체 프로필이 필요할 때 사용한다. "
                    + "대상 userSeq는 drive_admin_list_users 또는 drive_admin_search_users로 먼저 조회해 얻는다. "
                    + "항목에는 userSeq·userId(이메일)·userName·status(ACTIVE/INACTIVE/PENDING_DELETE/DELETED)·totalQuotaByte(할당량)·useQuotaByte(사용량)·"
                    + "roles(보유 역할 목록)·registerUserSeq(등록자)·registerDate·updateUserSeq·updateDate·countryCode·phoneNumber·"
                    + "profileImageKey·isMfaEnabled(MFA 사용 여부)·isProtected(보호 계정 — 테넌트 생성 때 만든 계정, 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·비밀번호 변경이 거부됨)가 포함된다. "
                    + "관리자 계정도 조회되며, 목록 툴에 나오지 않는 "
                    + "삭제 대기(PENDING_DELETE)·삭제 완료(DELETED) 계정도 userSeq를 알면 조회된다. "
                    + "목록 툴에 없는 필드(할당/사용 용량·전화번호·MFA 여부)까지 필요할 때 이 툴을 쓰고, "
                    + "여러 계정을 훑을 때는 drive_admin_list_users를, 이메일 여러 개의 상태만 한 번에 확인할 때는 drive_admin_get_users_status를 쓴다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "이 툴은 조회 전용이며 계정 정보를 변경하지 않는다.")
    public McpToolResult<AdminUserDetailToolResponse> getUser(@McpToolTenantUser @ToolParam(description = "대상 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_get_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details ->
                adminUserToolConverter.toDetailResponse(adminUserQueryUseCase.getUser(userSeq)));
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_activate_user",
            description = "관리자 전용 — 비활성(INACTIVE) 사용자 계정을 활성(ACTIVE)으로 전환한다 (웹 관리자 화면의 활성화 버튼과 동일 동작). "
                    + "'이 계정 다시 활성화해줘', '휴면 계정 풀어줘'처럼 비활성 계정을 되살릴 때 사용한다. "
                    + "대상 userSeq는 drive_admin_list_users(비활성 계정은 status=INACTIVE 필터, 관리자는 roles=ADMIN 필터)로 먼저 조회해 얻는다. "
                    + "일반 사용자와 관리자 계정 모두 대상이 된다. "
                    + "이미 활성(ACTIVE) 상태인 계정이면 에러(USER_017)가 반환된다 — 먼저 상세 조회로 status를 확인하고, ACTIVE면 호출하지 않는다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음). "
                    + "되돌리기(비활성화)는 drive_admin_deactivate_user를 사용한다.")
    public McpToolResult<Void> activateUser(@McpToolTenantUser @ToolParam(description = "활성화할 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_activate_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            adminUserUseCase.activateUser(details.getUserSeq(), userSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_deactivate_user",
            description = "관리자 전용 — 활성(ACTIVE) 사용자 계정을 비활성(INACTIVE)으로 전환한다 (웹 관리자 화면의 비활성화 버튼과 동일 동작). "
                    + "'이 계정 비활성화해줘', '이 사용자 로그인 막아줘'처럼 계정 사용을 중지시킬 때 사용한다. "
                    + "삭제가 아니다 — 계정과 데이터는 유지되며 로그인만 불가능해지고, drive_admin_activate_user로 되돌릴 수 있다. "
                    + "대상 userSeq는 drive_admin_list_users로 먼저 조회해 얻는다. "
                    + "일반 사용자와 관리자 계정 모두 대상이 되지만, 본인 계정과 기본 관리자 계정은 비활성화할 수 없다(에러 USER_011). "
                    + "보호 계정(isProtected=true — 테넌트 생성 때 만든 관리자 계정, 테넌트 유형 무관)도 비활성화할 수 없다(USER_034). "
                    + "이미 비활성(INACTIVE) 상태인 계정이면 에러(USER_018)가 반환된다 — 먼저 상세 조회로 status를 확인하고, INACTIVE면 호출하지 않는다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> deactivateUser(@McpToolTenantUser @ToolParam(description = "비활성화할 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_deactivate_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            adminUserUseCase.deactivateUser(details.getUserSeq(), userSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_update_user",
            description = "관리자 전용 — 사용자 계정의 정보(이름·할당 용량·국가코드·전화번호·프로필 이미지 삭제·역할)를 수정한다 "
                    + "(웹 관리자 화면의 사용자 정보 수정과 동일 동작). '이 사용자 이름 바꿔줘', '할당 용량 10GB로 늘려줘', "
                    + "'전화번호 수정해줘'처럼 계정 프로필을 변경할 때 사용한다 "
                    + "(여러 계정을 한 번에 수정할 때는 drive_admin_bulk_upsert_users — 단, 그 툴은 생략 값을 유지하지 않고 전체 교체한다). "
                    + "대상 userSeq는 drive_admin_list_users 또는 drive_admin_search_users로 먼저 조회해 얻는다. "
                    + "부분 수정이다 — 지정한 필드만 바뀌고 생략한 필드는 현재 값이 유지된다. "
                    + "할당 용량(totalQuotaByte)은 0보다 커야 하고(0 이하는 QUOTA_008) 현재 사용량보다 작게 줄일 수 없다(에러 반환) — 현재 사용량은 "
                    + "drive_admin_get_user_storage_usage로 확인한다. 늘리면 증가분이 테넌트 총 용량 안에 들어가야 한다(QUOTA_007). "
                    + "roles를 주면 보유 역할을 그 목록(USER·ADMIN 중 1개 이상, 그 외 USER_028)으로 교체하고, 생략하면 역할을 유지한다. "
                    + "USER를 새로 주면 totalQuotaByte(0보다 큼)를 함께 지정한다. USER를 빼면 할당 용량이 0이 되며, 사용 중인 용량이 있으면 거부된다(USER_030 — 파일 정리 후 회수). "
                    + "본인의 ADMIN 역할은 뺄 수 없다(USER_031). "
                    + "보호 계정(isProtected=true — 테넌트 생성 때 만든 관리자 계정, 테넌트 유형 무관)은 역할을 바꿀 수 없다(USER_034 — 같은 역할을 다시 보내는 것과 다른 필드 수정은 허용). "
                    + "시스템 관리형 테넌트(SYSTEM_MANAGED)의 보호 계정은 정보 수정 자체가 거부된다(TENANT_011 — 계정 값은 외부 시스템이 정함, 보호 아닌 계정은 수정 가능). "
                    + "수정 후 역할에 USER가 없으면(관리자만) 할당 용량은 0만 허용된다(0이 아니면 QUOTA_009). "
                    + "국가코드와 전화번호는 한 쌍이다 — 변경하려면 둘을 함께 지정하고(대상 계정에 국가코드가 없는 상태에서 전화번호만 지정하면 에러), "
                    + "삭제하려면 둘 다 빈 문자열(\"\")로 보낸다(웹에서 두 필드를 비우고 저장하는 것과 동일). "
                    + "유지할 필드에 빈 문자열을 보내지 않도록 주의한다 — 유지는 반드시 생략으로 표현한다. "
                    + "프로필 이미지: 새 이미지 업로드는 지원하지 않고(웹 관리자 화면 전용) deleteProfileImage=true로 삭제만 가능하다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음) — 변경 결과는 drive_admin_get_user로 확인한다.")
    public McpToolResult<Void> updateUser(
            @McpToolTenantUser @ToolParam(description = "수정할 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq,
            @ToolParam(description = "변경할 사용자 이름 — 최대 20자. 생략 시 현재 값 유지", required = false) String userName,
            @ToolParam(description = "변경할 할당 용량(byte). 현재 사용량 미만으로 줄일 수 없음. 생략 시 현재 값 유지", required = false) Long totalQuotaByte,
            @ToolParam(description = "변경할 전화번호 국가코드 — '+'를 뺀 숫자 (예: 82. '+82'로 보내도 '+'는 자동 제거). "
                    + "전화번호와 함께 지정. 생략 시 현재 값 유지, 전화번호와 함께 빈 문자열(\"\")을 보내면 삭제", required = false) String countryCode,
            @ToolParam(description = "변경할 전화번호 — '-'를 뺀 숫자 (예: 01012345678. '-'가 있어도 자동 제거). "
                    + "국가코드와 함께 지정. 생략 시 현재 값 유지, 국가코드와 함께 빈 문자열(\"\")을 보내면 삭제", required = false) String phoneNumber,
            @ToolParam(description = "프로필 이미지를 삭제하려면 true. 생략 시 현재 이미지 유지", required = false) Boolean deleteProfileImage,
            @ToolParam(description = "교체 후 보유할 역할 목록 — USER(일반 사용자)·ADMIN(관리자) 중 1개 이상. 생략 시 현재 역할 유지. "
                    + "현재 역할은 drive_admin_get_user 응답의 roles", required = false) List<RoleType> roles) {

        log.info("[MCP] drive_admin_update_user called. userSeq : {}, userName : {}, totalQuotaByte : {}, countryCode : {}, phoneNumber : {}, deleteProfileImage : {}",
                userSeq, userName, totalQuotaByte, countryCode, phoneNumber, deleteProfileImage);

        return McpToolResult.executeAuthed(details -> {

            McpToolParams.require(userSeq, "userSeq");

            // 하부 UPDATE SQL은 5개 컬럼 전체 교체라 "생략=유지"는 어댑터가 만든다 — 웹은 화면이 현재 값을
            // 재전송하는 구조(프로필 이미지도 sk로 현재 키 재전송)인데 MCP엔 그 화면이 없으므로,
            // 현재 값을 선조회해 생략된 필드를 채운 완전한 커맨드를 구성한다(웹과 동일한 usecase 계약 유지).
            var current = adminUserQueryUseCase.getUser(userSeq);

            String mergedUserName = userName != null ? userName : current.userName();
            McpToolParams.requireText(mergedUserName, "userName");
            McpToolParams.requireMaxLength(mergedUserName, USER_NAME_MAX_LENGTH, "userName"); // TB_USER.USER_NAME varchar(20)

            var command = AdminUpdateUserCommand.builder()
                    .userName(mergedUserName)
                    // 용량 생략 = 유지. 단 USER 를 빼는(관리자만 남는) 교체면 0 으로 확정되도록 비워 둔다(현재 값을 채우면 QUOTA_009)
                    .totalQuotaByte(totalQuotaByte != null ? totalQuotaByte
                            : roles != null && !roles.contains(RoleType.USER) ? null : current.totalQuotaByte())
                    // 전화 쌍은 웹 계약 그대로 — 빈 문자열 쌍이 오면 웹이 두 필드를 비우고 저장하는 것과 동일하게 삭제로 도달,
                    // 한쪽만 오면 병합 후 usecase의 동반 필수 검증이 판정(PHONE_FIELDS_REQUIRED_TOGETHER)
                    .countryCode(countryCode != null ? countryCode : current.countryCode())
                    .phoneNumber(phoneNumber != null ? phoneNumber : current.phoneNumber())
                    .deleteProfileImage(deleteProfileImage)
                    .roles(roles != null ? new HashSet<>(roles) : null) // 생략 = 역할 유지
                    .build();

            adminUserUseCase.updateUserByAdmin(details.getUserSeq(), userSeq, command);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_register_user",
            description = "관리자 전용 — 새 사용자 계정을 등록한다 (웹 관리자 화면의 사용자 등록과 동일 동작). "
                    + "'신규 계정 만들어줘', '이 이메일로 사용자 등록해줘'처럼 사용자가 등록을 명시적으로 요청한 경우에만 사용한다 "
                    + "(여러 명을 한 번에 등록·수정할 때는 drive_admin_bulk_upsert_users). "
                    + "userId는 이메일 형식의 로그인 ID다 — 이메일 형식이 아니거나 같은 테넌트에 이미 존재하는 ID면 에러(USER_004)를 반환한다 — 다른 테넌트에 있는 이메일은 등록할 수 있다. "
                    + "라이선스 좌석이 부족하면 등록이 거부된다. "
                    + "시스템 관리형 단일 사용자 테넌트(SYSTEM_MANAGED + SINGLE — 계정 1개 고정)에서는 등록이 거부된다(TENANT_011). "
                    + "roles는 USER·ADMIN 중 1개 이상이다(SUPER_ADMIN·ANONYMOUS는 USER_028, 빈 목록은 에러 \"roles는 1개 이상 지정해야 합니다.\") — 둘 다 주면 한 계정이 사용자 겸 관리자가 된다. "
                    + "할당 용량(totalQuotaByte)은 byte 단위다 — roles에 USER가 있으면 필수(0보다 커야 함, 0 이하는 QUOTA_008)이고 기존 사용자 할당량 합과 더해 테넌트 총 용량을 넘으면 거부된다(QUOTA_007). "
                    + "ADMIN만 주면 할당 용량이 없으므로 생략하거나 0으로 보낸다(0이 아니면 QUOTA_009). "
                    + "국가코드와 전화번호는 한 쌍이다 — 지정하려면 둘을 함께 보내고(한쪽만 보내면 에러), 생략하면 미설정으로 등록된다. "
                    + "프로필 이미지 업로드는 지원하지 않는다(웹 관리자 화면 전용). "
                    + "⚠️ password는 계정의 초기 비밀번호 원문이다 — 대화 기록에 평문으로 남으므로 실제 쓸 비밀번호가 아닌 "
                    + "임시 비밀번호를 지정하고, 등록 후 사용자가 직접 변경하도록 안내한다. "
                    + "성공 시 생성된 계정의 userSeq를 반환한다 — 상세는 drive_admin_get_user로 확인한다.")
    public McpToolResult<AdminUserRegisterToolResponse> registerUser(
            @ToolParam(description = "로그인 ID — 이메일 형식. 같은 테넌트에 이미 존재하는(활성·비활성·삭제 대기) 이메일이면 에러, 삭제 완료된 계정의 이메일과 다른 테넌트에 있는 이메일은 사용 가능") String userId,
            @ToolParam(description = "초기 비밀번호 — 8~64자, 대문자·소문자·숫자·특수문자 각 1자 이상 포함. "
                    + "임시 비밀번호 권장(대화 기록에 평문으로 남음)") String password,
            @ToolParam(description = "사용자 이름 — 최대 20자") String userName,
            @ToolParam(description = "할당 용량(byte) — roles에 USER가 있으면 필수(0보다 커야 함), ADMIN만이면 생략 또는 0", required = false) Long totalQuotaByte,
            @ToolParam(description = "부여할 역할 목록 — USER(일반 사용자)·ADMIN(관리자) 중 1개 이상. 둘 다 주면 사용자 겸 관리자 계정") List<RoleType> roles,
            @ToolParam(description = "전화번호 국가코드 — '+'를 뺀 숫자, 최대 4자 (예: 82. '+82'로 보내도 '+'는 자동 제거). "
                    + "전화번호와 함께 지정. 생략 시 미설정", required = false) String countryCode,
            @ToolParam(description = "전화번호 — '-'를 뺀 숫자, 최대 20자 (예: 01012345678. '-'가 있어도 자동 제거). "
                    + "국가코드와 함께 지정. 생략 시 미설정", required = false) String phoneNumber) {

        // 비밀번호는 로그에 남기지 않는다
        log.info("[MCP] drive_admin_register_user called. userId : {}, userName : {}, totalQuotaByte : {}, roleType : {}, countryCode : {}, phoneNumber : {}",
                userId, userName, totalQuotaByte, roles, countryCode, phoneNumber);

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.requireText(userId, "userId");
            McpToolParams.requireText(password, "password");
            McpToolParams.requireText(userName, "userName");
            McpToolParams.requireMaxLength(userName, USER_NAME_MAX_LENGTH, "userName"); // TB_USER.USER_NAME varchar(20) — 초과 시 SQL 예외라 선검증
            McpToolParams.requireNonEmpty(roles, "roles");
            // 허용 역할(USER/ADMIN)·할당 용량 필수 여부는 웹과 같이 usecase 가 판정(SUPER_ADMIN·ANONYMOUS 는 USER_028)

            // 이메일 형식·중복 ID·좌석 제한·quota 상한·전화 동반 필수는 웹과 동일하게 usecase/도메인이 검증한다(인밴드 변환).
            var command = AdminRegisterUserCommand.builder()
                    .userId(userId)
                    .encPwd(password)
                    .userName(userName)
                    .totalQuotaByte(totalQuotaByte)
                    .roles(new HashSet<>(roles))
                    .countryCode(countryCode)
                    .phoneNumber(phoneNumber)
                    .build();

            return new AdminUserRegisterToolResponse(
                    adminUserUseCase.registerUser(command, details.getUserSeq()));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_update_user_password",
            description = "관리자 전용 — 특정 사용자 계정의 비밀번호를 새 비밀번호로 즉시 변경한다 "
                    + "(웹 관리자 화면의 비밀번호 변경과 동일 동작). '이 계정 비밀번호 초기화해줘', "
                    + "'임시 비밀번호로 바꿔줘'처럼 사용자가 변경을 명시적으로 요청한 경우에만 사용한다. "
                    + "대상 userSeq는 drive_admin_list_users 또는 drive_admin_search_users로 먼저 조회해 얻는다. "
                    + "관리자 권한 변경이라 현재 비밀번호 확인 없이 바로 바뀐다 — 대상 계정의 로그인 실패 횟수도 "
                    + "함께 초기화되므로 잠긴 계정을 푸는 용도로도 쓴다. 일반 사용자와 관리자 계정 모두 대상이 된다. "
                    + "새 비밀번호는 8~64자에 대문자·소문자·숫자·특수문자를 각 1자 이상 포함해야 한다(위반 시 에러). "
                    + "보호 계정(isProtected=true — 테넌트 생성 때 만든 관리자 계정, 테넌트 유형 무관)은 비밀번호를 바꿀 수 없다(USER_034). "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "⚠️ newPassword는 비밀번호 원문이다 — 대화 기록에 평문으로 남으므로 실제 쓸 비밀번호가 아닌 "
                    + "임시 비밀번호를 지정하고, 변경 후 사용자가 직접 다시 변경하도록 안내한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> updateUserPassword(
            @McpToolTenantUser @ToolParam(description = "대상 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq,
            @ToolParam(description = "새 비밀번호 — 8~64자, 대문자·소문자·숫자·특수문자 각 1자 이상 포함. "
                    + "임시 비밀번호 권장(대화 기록에 평문으로 남음)") String newPassword) {

        // 비밀번호는 로그에 남기지 않는다
        log.info("[MCP] drive_admin_update_user_password called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.require(userSeq, "userSeq");
            McpToolParams.requireText(newPassword, "newPassword");

            // 비밀번호 정책 위반(USER_001)·미존재 사용자는 웹과 동일하게 usecase/도메인이 검증한다(인밴드 변환).
            adminUserUseCase.updatePasswordByAdmin(userSeq, newPassword, currentHttpRequest());
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_withdraw_user",
            description = "관리자 전용 — 사용자 계정을 탈퇴(삭제 대기) 처리한다 (웹 관리자 화면의 사용자 삭제와 동일 동작). "
                    + "'이 계정 탈퇴 처리해줘', '이 사용자 삭제해줘'처럼 사용자가 명시적으로 요청한 경우에만, "
                    + "대상 계정(userSeq·userId)을 사용자에게 확인시킨 후 사용한다. "
                    + "⚠️ 파괴적 작업이다 — 즉시 status가 PENDING_DELETE(삭제 대기)로 바뀌어 로그인이 차단되고, "
                    + "약 30일 보관 후 스케줄러가 계정과 데이터를 완전 삭제한다(완전 삭제 후에는 복구 불가). "
                    + "보관 기간 내에는 drive_admin_restore_user(또는 웹 관리자 화면의 '삭제 대기 사용자' 목록)로 복원할 수 있다(복원 시 INACTIVE 상태). "
                    + "삭제 대기 계정 현황은 drive_admin_list_pending_delete_users로 조회한다. 보관 기간 없이 즉시 완전 삭제해야 하면 drive_admin_delete_user(복구 불가)를 사용한다. "
                    + "단순히 로그인만 막으려면 이 툴이 아니라 drive_admin_deactivate_user를 사용한다. "
                    + "대상 userSeq는 drive_admin_list_users 또는 drive_admin_search_users로 먼저 조회해 얻는다. "
                    + "활성(ACTIVE)·비활성(INACTIVE) 계정 모두 대상이 되지만, 본인 계정과 기본 관리자 계정은 탈퇴 처리할 수 없다(에러 USER_012). "
                    + "보호 계정(isProtected=true — 테넌트 생성 때 만든 관리자 계정, 테넌트 유형 무관)도 탈퇴 처리할 수 없다(USER_034). "
                    + "이미 삭제 대기(PENDING_DELETE) 상태인 계정이면 에러(USER_016)가 반환된다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> withdrawUser(
            @McpToolTenantUser @ToolParam(description = "탈퇴 처리할 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_withdraw_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            // 본인·기본 관리자 차단(USER_012)·이미 삭제 대기(USER_016)는 웹과 동일하게 usecase가 검증한다(인밴드 변환).
            adminUserUseCase.withdraw(details.getUserSeq(), userSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    /**
     * CHANGE_PASSWORD 사용자 로그(IP·지역 기록)가 요구하는 HttpServletRequest —
     * MCP 툴은 Streamable HTTP 요청 스레드에서 동기 실행되므로 요청 컨텍스트가 항상 존재한다
     * (McpToolLocaleAspect의 Accept-Language 조회와 동일 전제).
     */
    private static HttpServletRequest currentHttpRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("HTTP 요청 컨텍스트가 없습니다 — MCP 툴은 요청 스레드에서 실행되어야 합니다");
    }

}
