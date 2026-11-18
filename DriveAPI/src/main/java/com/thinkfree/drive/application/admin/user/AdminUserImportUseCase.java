package com.thinkfree.drive.application.admin.user;

import com.thinkfree.drive.api.response.ByteDownloadResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.application.admin.command.AdminImportCsvCommand;
import com.thinkfree.drive.application.admin.converter.AdminCommandConverter;
import com.thinkfree.drive.application.admin.csv.AdminUserCsvImportRow;
import com.thinkfree.drive.application.admin.info.AdminImportCsvInfo;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.admin.user.converter.AdminUserCommandConverter;
import com.thinkfree.drive.application.admin.user.converter.AdminUserInfoConverter;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkUpsertInfo;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.common.file.domain.FileKey;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.common.validation.EmailValidator;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.csv.constans.CsvConstants;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.service.CsvParsingService;
import com.thinkfree.drive.csv.support.CsvWriter;
import com.thinkfree.drive.event.user.UserCsvRegisteredEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.tenant.TenantValidator;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.policy.TenantQuotaPolicy;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.enums.AdminFileTemplateType;
import com.thinkfree.drive.user.admin.result.AdminUserInsertResult;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.util.DateUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.StringUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 진입점(내부 API·MCP) 공용 관리자 계정 가져오기 usecase — 여러 계정을 한 번에 등록/수정(JSON 일괄 upsert·CSV 가져오기).
 * 단건 변경은 {@link AdminUserUseCase}, 조회는 {@link AdminUserQueryUseCase}.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * (예외: Streaming/ByteDownloadResponse는 api.v1이 아닌 공용 다운로드 봉투 — 도메인 서비스도 사용.)
 * 접근 제어는 어댑터 책임 — 웹은 시큐리티 URL role 게이트(/api/v1/admins/** = ADMIN),
 * MCP는 툴 클래스의 @McpToolRole(ADMIN) 어스펙트가 담당한다.
 * 테넌트 격리는 TenantContext 기반 — bulkUpsert 의 수정 대상 테넌트 경계는 usecase 내부 tenantValidator가 자체 검사한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserImportUseCase {

    private final AdminCommandConverter adminCommandConverter;
    private final AdminUserCommandConverter adminUserCommandConverter;
    private final AdminUserInfoConverter adminUserInfoConverter;
    private final UserService userService;
    private final UserFindService userFindService;
    private final AdminUserService adminUserService;
    private final FileProvider fileProvider;
    private final CsvParsingService csvParsingService;
    private final LicenseService licenseService;
    private final SeatLicenseService seatLicenseService;
    private final QuotaService quotaService;
    private final TenantUserQuotaGuard tenantUserQuotaGuard;
    private final TenantValidator tenantValidator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final BrandingMetaService brandingMetaService;
    private final UserAuditLookupService userAuditLookupService;
    private final UserAuditService userAuditService;
    private final UserRoleService userRoleService;
    private final UserRoleFindService userRoleFindService;
    private final TenantPolicy tenantPolicy;

    @AuditLog
    @Transactional
    public AdminUserBulkUpsertInfo bulkUpsert(AdminUserBulkUpsertCommand command, int loginUserSeq) {

        int tenantSeq = TenantContext.getOrThrow();
        // 테넌트 정책은 항목 검증·조회·잠금 전에 — 등록 항목이 있으면 SYSTEM_MANAGED + SINGLE 거부
        if (command.users().stream().anyMatch(UserItemCommand::isInsert)) {
            tenantPolicy.require(tenantSeq, TenantFeature.USER_REGISTER);
        }

        var partitioned = command.partition();
        List<UserItemCommand> inserts = partitioned.inserts();
        List<UserItemCommand> updates = partitioned.updates();

        if (!inserts.isEmpty()) {
            if (!seatLicenseService.isSeatAvailable(tenantSeq, inserts.size())) {
                throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
            }
        }

        List<String> insertUserIds = new ArrayList<>();
        List<UserItemCommand> resolvedInserts = new ArrayList<>(inserts.size());
        long insertQuotaSum = 0;

        for (UserItemCommand item : inserts) {

            EmailValidator.validateOrThrow(item.userId());
            Password.validatePasswordPattern(item.encPwd());

            // 역할 먼저(USER/ADMIN 1개 이상 — 단건 등록과 같은 규칙), 그 역할로 할당량 확정(USER 보유면 필수·0 보다 큼, ADMIN 만이면 0·생략 시 0)
            DriveAssert.throwIfFalse(RoleType.isAssignable(item.roles()), UserErrorCode.INVALID_ROLE_PARAM);
            UserItemCommand resolved = item.withTotalQuotaByte(TenantQuotaPolicy.resolveUserQuota(item.roles(), item.totalQuotaByte()));
            resolvedInserts.add(resolved);
            insertQuotaSum = TenantQuotaPolicy.addQuota(insertQuotaSum, resolved.totalQuotaByte());
            insertUserIds.add(item.userId());
        }
        inserts = resolvedInserts;

        if (!insertUserIds.isEmpty()) {
            // 한 요청 안의 같은 userId 도 중복 — DB 대조만으로는 통과해 INSERT 유니크 위반이 된다
            DriveAssert.throwIfFalse(duplicatesOf(insertUserIds).isEmpty(), UserErrorCode.DUPLICATE_EMAIL);
            Set<String> existing = userFindService.findExistingUserIds(tenantSeq, insertUserIds);
            if (!existing.isEmpty()) {
                throw ApiRuntimeException.type(UserErrorCode.DUPLICATE_EMAIL);
            }
        }

        List<Integer> userSeqList = updates.stream().map(UserItemCommand::userSeq).toList();
        long updateQuotaSum = 0; // 스토리지(Ceph) 가용량 판정용 — 늘어나는 수정 항목의 (새 값 − 사용량) 합

        if (!updates.isEmpty()) {

            // 테넌트 경계 사전검증 (다른 테넌트 user는 여기서 404)
            tenantValidator.assertSameTenant(userSeqList);
            // SYSTEM_MANAGED 보호 계정 수정 거부(하나라도 있으면 전체) — 테넌트 경계 확인 뒤(다른 테넌트 계정의 보호 여부가 드러나지 않게), 스토리지 확인·잠금 전
            userSeqList.forEach(tenantPolicy::requireUpdatableUser);

            // 스토리지 가용량은 잠금 전에 — 잠금 없이 읽은 현재 값 기준(Ceph 는 테넌트끼리 공유·예약 없음이라 잠금 안이어도 근사 검사)
            Map<Integer, UserDetailData> currentDetails = userFindService.findDetailsByUserSeqs(userSeqList).stream()
                    .collect(Collectors.toMap(UserDetailData::getUserSeq, Function.identity(), (a, b) -> a));
            Map<Integer, Set<RoleType>> currentRoles = userRoleFindService.findRolesByUserSeqs(userSeqList);
            for (UserItemCommand item : updates) {
                UserDetailData current = currentDetails.get(item.userSeq());
                Set<RoleType> roles = currentRoles.getOrDefault(item.userSeq(), Set.of());
                if (current == null || !RoleType.isAssignable(roles)) {
                    throw ApiRuntimeException.type(UserErrorCode.BULK_UPSERT_USER_NOT_FOUND);
                }
                // roles 생략 = 역할 유지, 지정하면 USER/ADMIN 1개 이상
                DriveAssert.throwIfTrue(item.roles() != null && !RoleType.isAssignable(item.roles()), UserErrorCode.INVALID_ROLE_PARAM);
                // 보호 계정 역할 변경은 스토리지 확인·잠금 전에 거부(같은 역할 재전송은 변경 아님) — 잠금 뒤 replaceRoles 가 같은 규칙으로 최종 판정
                if (item.roles() != null && !item.roles().equals(roles)) {
                    tenantPolicy.requireManageableUser(item.userSeq());
                }
                long newQuota = TenantQuotaPolicy.resolveUserQuota(item.roles() != null ? item.roles() : roles, item.totalQuotaByte());
                if (newQuota > current.getTotalQuotaByte()) {
                    updateQuotaSum = TenantQuotaPolicy.addQuota(updateQuotaSum, newQuota - current.getUseQuotaByte());
                }
            }
        }

        long totalQuotaIncrease = TenantQuotaPolicy.addQuota(insertQuotaSum, updateQuotaSum);

        if (totalQuotaIncrease > 0) {
            try {
                if (!quotaService.canIncrease(totalQuotaIncrease)) {
                    throw new ApiRuntimeException(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
                }
            } catch (ExternalApiException e) {
                log.error("bulkUpsert.canIncrease Exception : ", e);
                throw new ApiRuntimeException(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
            }
        }

        Map<Integer, User> currentUsersMap = Map.of();

        // 테넌트 행은 여기서 한 번만 잠근다 — 좌석 소비(PERSEAT)·테넌트 총 용량 판정·사용자 행 잠금보다 먼저(활성화와 같은 테넌트 → 사용자 순서, 교착 방지).
        // 이후 판정은 ensureAssignableLocked(다시 잠그지 않음)
        var seatPolicy = licenseService.getSeatPolicy(tenantSeq);
        tenantUserQuotaGuard.lockTenant(tenantSeq);

        if (!updates.isEmpty()) {

            List<User> currentUsers = userService.lockUsersBySeqList(userSeqList);

            if (currentUsers.size() != userSeqList.size()) {
                throw ApiRuntimeException.type(UserErrorCode.BULK_UPSERT_USER_NOT_FOUND);
            }

            currentUsersMap = currentUsers.stream().collect(Collectors.toMap(User::getUserSeq, Function.identity()));
        }

        // 잠근 값으로 다시 판정 — 보유 역할(USER/ADMIN), 역할 교체 규칙, 역할별 할당량, 사용량 미만 금지, 테넌트 총 용량
        // 역할 변경은 사용자 행 잠금으로 직렬화된다(역할을 바꾸는 경로는 모두 이 잠금 뒤)
        Map<Integer, Set<RoleType>> lockedRoles = updates.isEmpty() ? Map.of() : userRoleFindService.findRolesByUserSeqs(userSeqList);
        long updateAllocationDelta = 0; // 테넌트 총 용량 판정용 — 수정 대상의 (새 값 − 기존 값) 합(감소분 상쇄)
        List<UserItemCommand> resolvedUpdates = new ArrayList<>(updates.size());

        for (UserItemCommand item : updates) {

            User current = currentUsersMap.get(item.userSeq());

            // 수정 대상도 보유 역할이 전부 등록 가능한 역할(USER/ADMIN)인 계정만 — SUPER_ADMIN·ANONYMOUS 는 설치 시 계정이라 대상이 아니다
            Set<RoleType> roles = lockedRoles.getOrDefault(item.userSeq(), Set.of());
            if (!RoleType.isAssignable(roles)) {
                throw ApiRuntimeException.type(UserErrorCode.BULK_UPSERT_USER_NOT_FOUND);
            }

            Set<RoleType> resultRoles = item.roles() != null ? item.roles() : roles;
            if (item.roles() != null) {
                // 본인 ADMIN 회수·사용량 있는 USER 회수 거부 — 용량 판정보다 먼저. 한 항목이라도 실패하면 전체 롤백
                userRoleService.replaceRoles(new UserRoleChangeDto(item.userSeq(), resultRoles, loginUserSeq, current.getUseQuotaByte()));
            }

            // 교체 후 보유 역할로 할당량 확정(USER 보유면 필수·0 보다 큼, ADMIN 만이면 0·생략 시 0)
            UserItemCommand resolved = item.withRoles(resultRoles)
                    .withTotalQuotaByte(TenantQuotaPolicy.resolveUserQuota(resultRoles, item.totalQuotaByte()));
            resolvedUpdates.add(resolved);
            long newQuota = resolved.totalQuotaByte();

            if (current.isInvalidTotalQuotaChange(newQuota)) {
                throw ApiRuntimeException.type(UserErrorCode.QUOTA_DECREASE_NOT_ALLOWED);
            }

            updateAllocationDelta = TenantQuotaPolicy.addQuota(updateAllocationDelta, newQuota - current.getTotalQuotaByte());
        }
        updates = resolvedUpdates;

        // 테넌트 총 용량: 기존 사용자 할당량 합 + 신규 합 + 수정 증감 합 ≤ 총 용량
        tenantUserQuotaGuard.ensureAssignableLocked(tenantSeq, TenantQuotaPolicy.addQuota(insertQuotaSum, updateAllocationDelta));

        var insertDtos = adminUserCommandConverter.toSaveDtos(inserts, loginUserSeq, tenantSeq);
        var updateDtos = adminUserCommandConverter.toBulkUpdateDtos(updates);

        // currentUsersMap(lockUsersBySeqList)은 잠금으로 로드한 관리 엔티티지만 upsertAll 은 배치 UPDATE 라 그 인스턴스는 배치 전 값으로 남는다(고치지 않음).
        // 감사 로그 "변경 전" 스냅샷(DTO)은 upsertAll(update 실행) 전에 별도로 조회한다.
        Map<Integer, UserDetailData> beforeAuditUsers = updates.stream()
                .map(UserItemCommand::userSeq)
                .collect(Collectors.toMap(Function.identity(), userAuditLookupService::findUser));

        var insertedUsers = adminUserService.upsertAll(insertDtos, updateDtos, seatPolicy);
        log.info("[사용자 일괄 등록/수정] insert: {}건, update: {}건",
                inserts.size(), updates.size());

        recordBulkUpsertAudit(insertedUsers, updates, beforeAuditUsers);

        return new AdminUserBulkUpsertInfo(
                inserts.size(),
                updates.size(),
                adminUserInfoConverter.toInsertedUserInfos(insertedUsers)
        );
    }

    public StreamingDownloadResponse importCsvSample(){
        var adminFileTemplateType = AdminFileTemplateType.USER_IMPORT_CSV;
        var fileKey = new FileKey(adminFileTemplateType.getClasspath());
        var fileData = fileProvider.getFileData(fileKey);
        var streamingResponseBody = ResourceUtil.createFileStream(fileData);
        var contentDisposition = ResourceUtil.createContentDisposition(adminFileTemplateType.getFileName());
        return new StreamingDownloadResponse(contentDisposition, fileData.contentLength(), MediaType.parseMediaType(fileData.contentType()), streamingResponseBody);
    }

    @AuditLog
    @Transactional
    public AdminImportCsvInfo importCsv(AdminImportCsvCommand command, int loginUserSeq) {

        // 테넌트 정책은 파싱·검증 전에(실패 CSV 가 아니라 403 TENANT_011) — SYSTEM_MANAGED + SINGLE 등록 거부, 안내 메일 요청은 SYSTEM_MANAGED 거부
        int policyTenantSeq = TenantContext.getOrThrow();
        tenantPolicy.require(policyTenantSeq, TenantFeature.USER_REGISTER);
        if (Boolean.TRUE.equals(command.sendEmail())) {
            tenantPolicy.require(policyTenantSeq, TenantFeature.MAIL);
        }

        var csvParseResult = csvParsingService.csvParse(command.file(), AdminUserCsvImportRow.class);

        if(csvParseResult.isGlobalFail()){
            var bytes = CsvWriter.write(csvParseResult.globalError().getMessage());
            var byteDownloadResponse = createByteDownloadResponse(bytes);
            return new AdminImportCsvInfo.Fail(byteDownloadResponse);
        }

        var successes = csvParseResult.successes();

        if(csvParseResult.allSuccess()) {

            var successesSize = successes.size();

            var isSeatAvailable = seatLicenseService.isSeatAvailable(TenantContext.getOrThrow(), successesSize);

            if(!isSeatAvailable){
                var bytes = CsvWriter.write(CsvErrorCode.ROW_LIMIT_EXCEEDED.getMessage());
                var byteDownloadResponse = createByteDownloadResponse(bytes);
                return new AdminImportCsvInfo.Fail(byteDownloadResponse);
            }

            var userIds = successes.stream()
                                   .map(AdminUserCsvImportRow::getUserId)
                                   .toList();

            // 이미 같은 테넌트에 있는 userId + 파일 안에서 두 번 이상 나온 userId 행을 실패로 돌려준다
            Set<String> existing = new HashSet<>(userFindService.findExistingUserIds(TenantContext.getOrThrow(), userIds));
            existing.addAll(duplicatesOf(userIds));

            if (!existing.isEmpty()) {

                var failedRows = csvParseResult.dataList()
                                               .stream()
                                               .filter(r -> existing.contains(r.data().getUserId()))
                                               .peek(r -> log.error("duplicate userId: {}", r.data().getUserId()))
                                               .map(r -> r.withFailureReason(CsvErrorCode.INVALID_VALUE.getMessage()))
                                               .toList();

                var bytes = CsvWriter.write(csvParseResult.headers(), failedRows);
                return new AdminImportCsvInfo.Fail(createByteDownloadResponse(bytes));
            }

            int tenantSeq = TenantContext.getOrThrow();
            var saveDtos = adminCommandConverter.toCsvSaveDtos(successes, loginUserSeq, tenantSeq);

            // 0 이하(-1 무제한 포함) 행이 있으면 거부 — CSV 는 요청 검증(@Positive)을 거치지 않는다
            if (saveDtos.stream().anyMatch(dto -> dto.totalQuotaByte() <= 0)) {
                var bytes = CsvWriter.write(CsvErrorCode.INVALID_VALUE.getMessage());
                return new AdminImportCsvInfo.Fail(createByteDownloadResponse(bytes));
            }

            long sumTotalQuotaBytes;
            try {
                sumTotalQuotaBytes = saveDtos.stream().mapToLong(AdminUserSaveDto::totalQuotaByte).reduce(0L, TenantQuotaPolicy::addQuota);
            } catch (ApiRuntimeException e) {
                // 행 합이 long 범위를 넘으면(TENANT_QUOTA_EXCEEDED) 총 용량 초과와 같은 실패 파일
                var bytes = CsvWriter.write(CsvErrorCode.STORAGE_ALLOCATION_EXCEEDED.getMessage());
                return new AdminImportCsvInfo.Fail(createByteDownloadResponse(bytes));
            }

            // 테넌트 총 용량: 기존 사용자 할당량 합 + 이번 행 합 ≤ 총 용량 (테넌트 행 잠금은 이 트랜잭션 끝까지)
            if (!tenantUserQuotaGuard.isAssignable(tenantSeq, sumTotalQuotaBytes)) {
                log.error("importCsv - tenant quota exceeded");
                var bytes = CsvWriter.write(CsvErrorCode.STORAGE_ALLOCATION_EXCEEDED.getMessage());
                return new AdminImportCsvInfo.Fail(createByteDownloadResponse(bytes));
            }

            try {

                var canIncrease = quotaService.canIncrease(sumTotalQuotaBytes);

                if (!canIncrease) {
                    log.error("importCsv - storage full");
                    var bytes = CsvWriter.write(CsvErrorCode.STORAGE_ALLOCATION_EXCEEDED.getMessage());
                    var byteDownloadResponse = createByteDownloadResponse(bytes);
                    return new AdminImportCsvInfo.Fail(byteDownloadResponse);
                }

            }catch (ExternalApiException e){
                log.error("importCsv.canIncrease Exception : ", e);
                var bytes = CsvWriter.write(CsvErrorCode.UNKNOWN_ERROR.getMessage());
                var byteDownloadResponse = createByteDownloadResponse(bytes);
                return new AdminImportCsvInfo.Fail(byteDownloadResponse);
            }

            try {
                var seatPolicy = licenseService.getSeatPolicy(tenantSeq);
                var insertResult = adminUserService.registerAll(saveDtos, seatPolicy);

                for (AdminUserInsertResult user : insertResult) {
                    userAuditService.record(EventName.USER_CREATE, user.getUserSeq().longValue(), user.getUserId(), user.getUserName()); // 방금 INSERT 한 엔티티 — 신원 값만 넘김(record(EventName, UserIdentity) 와 같은 기록)
                }

                if(Boolean.TRUE.equals(command.sendEmail())){
                    var registeredUserList = successes.stream()
                                                      .map(s -> new UserCsvRegisteredEvent.RegisteredUser(s.getUserId(), s.getPw()))
                                                      .toList();

                    var locale = LocaleContextHolder.getLocale();
                    var serviceName = brandingMetaService.findServiceNameOrDefault(tenantSeq);
                    var admin = new UserCsvRegisteredEvent(locale, registeredUserList, serviceName, TenantContext.getOrThrow());

                    applicationEventPublisher.publishEvent(admin);
                }

                return new AdminImportCsvInfo.Success(insertResult.size());

            }catch (ApiRuntimeException e){
                log.error("registerAdminAll Exception : ", e);
                // 안쪽 트랜잭션 서비스를 지난 예외가 이미 트랜잭션을 rollback-only 로 표시했다 — 이 메서드가 직접 롤백을 표시해야
                // 커밋 시점에 UnexpectedRollbackException(500) 대신 조용히 롤백되고 실패 CSV 가 응답된다(일부 등록분도 함께 롤백)
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                var bytes = CsvWriter.write(CsvErrorCode.UNKNOWN_ERROR.getMessage());
                var byteDownloadResponse = createByteDownloadResponse(bytes);
                return new AdminImportCsvInfo.Fail(byteDownloadResponse);
            }
        }

        var bytes = CsvWriter.write(csvParseResult.headers(), csvParseResult.failures());
        var byteDownloadResponse = createByteDownloadResponse(bytes);
        return new AdminImportCsvInfo.Fail(byteDownloadResponse);

    }

    // 일괄 등록/수정 감사 로그: insert된 유저마다 USER/ADMIN_CREATE, update된 유저마다 변경분 있을 때 USER/ADMIN_INFO_UPDATE.
    private void recordBulkUpsertAudit(List<AdminUserInsertResult> insertedUsers, List<UserItemCommand> updates, Map<Integer, UserDetailData> beforeAuditUsers) {
        for (AdminUserInsertResult user : insertedUsers) {
            EventName eventName = user.getRoles().contains(RoleType.ADMIN) ? EventName.ADMIN_CREATE : EventName.USER_CREATE;
            userAuditService.record(eventName, user.getUserSeq().longValue(), user.getUserId(), user.getUserName()); // 방금 INSERT 한 엔티티 — 신원 값만 넘김(record(EventName, UserIdentity) 와 같은 기록)
        }

        for (UserItemCommand item : updates) {
            UserDetailData before = beforeAuditUsers.get(item.userSeq());
            if (before == null) {
                continue;
            }
            userAuditService.recordInfoUpdate(before, item.userName(), item.totalQuotaByte(), item.countryCode(), item.phoneNumber());
        }
    }

    private ByteDownloadResponse createByteDownloadResponse(byte[] bytes){
        var now = DateUtil.format(Instant.now(), DriveConstants.YYYYMMDD_HHMMSS);
        var fileName = StringUtil.append(CsvConstants.FAILURE_CSV_NAME, DriveConstants.HYPHEN, now, CsvConstants.CSV_EXTENSION);
        var contentDisposition = ResourceUtil.createContentDisposition(fileName);
        return new ByteDownloadResponse(contentDisposition, (long) bytes.length, CsvConstants.TEXT_CSV_UTF8, bytes);
    }

    /** 목록 안에서 두 번 이상 나온 userId */
    private static Set<String> duplicatesOf(List<String> userIds) {
        Set<String> seen = new HashSet<>();
        return userIds.stream().filter(userId -> !seen.add(userId)).collect(Collectors.toSet());
    }

}
