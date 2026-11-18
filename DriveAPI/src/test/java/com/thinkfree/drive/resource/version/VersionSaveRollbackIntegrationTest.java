package com.thinkfree.drive.resource.version;

import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.application.version.VersionUseCase;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.resource.version.service.ResourceVersionRetryService;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveDto;
import com.thinkfree.drive.weboffice.service.WebOfficeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 5차 패스 작업 D — 버전 저장(메타 커밋) → 스토리지 → 실패 시 보상 롤백이 업로드 전 상태로 되돌리는지(PostgreSQL Testcontainers).
 * 확인 대상 = tb_resource(크기·버전 번호·최신 소유자·버전 등록일·수정자·수정일), tb_resource_version(행·번호·상태·크기), tb_user.use_quota_byte.
 * <p>
 * 저장·롤백이 {@code REQUIRES_NEW} 라 롤백형 테스트로는 앞서 넣은 행이 안 보인다 → 트랜잭션 없이 커밋하고 끝에 지운다(WebOfficeSaveAuditIntegrationTest 와 같은 방식).
 * 스토리지·후처리(알림)·감사 기록은 목. 버전 한도 2 — 성공 저장 2회로 버전 1·2 를 만든 뒤 3번째 저장이 가장 오래된 버전을 밀어낸다.
 * 소유자 = ADMIN(2), 웹오피스 편집자(loginUserSeq) = SUPER_ADMIN(1). 세 흐름 모두 FileWriteOrchestrator 의 VERSION 핸들러(VersionFileWriteService)를 탄다 —
 * 롤백은 스토리지 실패에만, finalize·후처리 실패는 저장 유지(D1).
 */
@SpringBootTest(properties = "resource.versions.limit=2")
@ActiveProfiles("test")
@DisplayName("버전 저장 → 스토리지 실패 → 보상 롤백 — 리소스·버전 행·사용량이 업로드 전과 같다")
class VersionSaveRollbackIntegrationTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private WebOfficeService webOfficeService;
    @Autowired
    private FileUseCase fileUseCase;
    @Autowired
    private VersionUseCase versionUseCase;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @MockitoBean
    private ResourceAuditService resourceAuditService;
    @MockitoBean
    private NotificationRetryService notificationRetryService;
    @MockitoSpyBean
    private ResourceVersionRetryService resourceVersionRetryService;

    private Long resourceSeq;
    private Long quotaBefore;
    private Long totalQuotaBefore;

    @BeforeEach
    void quota() {
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        if (resourceSeq != null) {
            jdbc.update("DELETE FROM tb_resource_version WHERE resource_seq = ?", resourceSeq);
            jdbc.update("DELETE FROM tb_resource WHERE resource_seq = ?", resourceSeq);
            jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE ? OR storage_key LIKE ?", "%/" + resourceSeq, "%/" + resourceSeq + "/%");
        }
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    private int storageDeletionCount(String storageKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key = ?", Integer.class, storageKey);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 크기 10 파일(버전 1) — 수정자·수정일은 알려진 과거 값 */
    private void createFile() {
        loginAs(ADMIN);
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("rollback.docx").originalFileName("rollback.docx").copyNumber(0).ext("docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document").sizeByte(10L)
                .versionRegisterDate(Instant.parse("2026-01-01T00:00:00Z")).registerUserSeq(ADMIN).build();
        // save 뒤 저장 이름은 변경 감지 — 트랜잭션 안에서(밖이면 save 뒤 분리 상태라 UPDATE 가 안 나감, W10)
        transactionTemplate.executeWithoutResult(s -> {
            resourceRepository.save(r);
            r.initResourceSaveName();
        });
        resourceSeq = r.getResourceSeq();

        SecurityContextHolder.clearContext();
        jdbc.update("UPDATE tb_resource SET update_user_seq = 1, update_date = TIMESTAMPTZ '2026-02-02 02:02:02+00' WHERE resource_seq = ?", resourceSeq);
    }

    /** 웹오피스 저장(시큐리티 없음) — 스토리지 목이 성공하면 버전 이력 1행 추가 */
    private void webOfficeSave(long size) {
        webOfficeService.save(WebOfficeSaveDto.builder()
                .loginUserSeq(SUPER_ADMIN).userSeq(ADMIN).resourceSeq(resourceSeq).size(size)
                .inputStream(new ByteArrayInputStream(new byte[(int) size])).build());
    }

    /** 비교용 스냅샷 — 리소스 행·버전 행(seq 순)·사용량 */
    private Map<String, Object> snapshot() {
        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, current_version_number, latest_owner_seq, version_register_date, "
                + "update_user_seq, update_date FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        List<Map<String, Object>> versions = jdbc.queryForList("SELECT resource_version_seq, version_number, status, size_byte, owner_seq "
                + "FROM tb_resource_version WHERE resource_seq = ? ORDER BY resource_version_seq", resourceSeq);
        Long quota = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        return Map.of("resource", resource, "versions", versions, "quota", quota);
    }

    private void storageCopyAndUploadFails() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).copyAndUpload(any(FileData.class), anyString(), anyString(), anyString());
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).copyAndUpload(any(MultipartFile.class), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("웹오피스 저장 실패(시큐리티 없음, 밀려나는 버전 없음) — 새 버전 행 없음·크기·버전 번호·수정자·수정일·사용량 원복")
    void webOfficeStorageFailure() {
        createFile();
        Map<String, Object> before = snapshot();
        storageCopyAndUploadFails();

        assertThatThrownBy(() -> webOfficeSave(30L)).isNotNull();

        Mockito.verify(storageService).copyAndUpload(any(FileData.class), anyString(), anyString(), anyString()); // 메타 저장(REQUIRES_NEW 커밋) 뒤에 도달
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("버전 한도에서 저장 실패 — 밀려났던 가장 오래된 버전이 SUCCESS·원래 번호로, 사용량(밀어낼 때 뺀 크기 포함) 원복")
    void webOfficeStorageFailureAfterPushOut() {
        createFile();
        webOfficeSave(20L);
        webOfficeSave(40L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource_version WHERE resource_seq = ?", Integer.class, resourceSeq))
                .as("한도 2 — 버전 1·2").isEqualTo(2);
        Map<String, Object> before = snapshot();
        storageCopyAndUploadFails();

        assertThatThrownBy(() -> webOfficeSave(80L)).isNotNull();

        Mockito.verify(storageService, Mockito.times(3)).copyAndUpload(any(FileData.class), anyString(), anyString(), anyString());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("파일 버전 업로드(FileUseCase.upload VERSION, 시큐리티 있음) 실패 — 원복")
    void fileVersionUploadStorageFailure() {
        createFile();
        webOfficeSave(20L);
        Map<String, Object> before = snapshot();
        storageCopyAndUploadFails();
        loginAs(ADMIN);

        MockMultipartFile file = new MockMultipartFile("file", "rollback.docx", "application/octet-stream", new byte[50]);
        assertThatThrownBy(() -> fileUseCase.upload(new FileUploadCommand(null, null, resourceSeq, file, ResourceSaveType.VERSION), ADMIN, false, false))
                .isInstanceOf(StorageException.class);

        Mockito.verify(storageService).copyAndUpload(any(MultipartFile.class), anyString(), anyString(), anyString());
        assertThat(snapshot()).isEqualTo(before);
    }

    /** 재시도 제외 — 용량 부족은 다시 해도 같으므로 @ApiRetryable 이 재시도하지 않는다(재시도마다 REQUIRES_NEW 로 커밋되는 용량 초과 알림이 3건 쌓이던 문제). */
    @Test
    @DisplayName("용량 부족 버전 업로드 — IS_FULL 즉시 전파, 용량 초과 알림 1회, 메타·사용량 변화 없음, 스토리지 호출 없음")
    void quotaFullIsNotRetried() {
        createFile();
        jdbc.update("UPDATE tb_user SET use_quota_byte = total_quota_byte WHERE user_seq = ?", ADMIN);
        Map<String, Object> before = snapshot();
        loginAs(ADMIN);

        MockMultipartFile file = new MockMultipartFile("file", "rollback.docx", "application/octet-stream", new byte[50]);
        assertThatThrownBy(() -> fileUseCase.upload(new FileUploadCommand(null, null, resourceSeq, file, ResourceSaveType.VERSION), ADMIN, false, false))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(notificationRetryService, Mockito.times(1)).quotaExceededNotify(any());
        verify(storageService, never()).copyAndUpload(any(MultipartFile.class), anyString(), anyString(), anyString());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("버전 복원(VersionUseCase.restoreVersion) 스토리지 실패 — 원복")
    void versionRestoreStorageFailure() {
        createFile();
        webOfficeSave(20L);
        Long versionSeq = jdbc.queryForObject("SELECT resource_version_seq FROM tb_resource_version WHERE resource_seq = ?", Long.class, resourceSeq);
        Map<String, Object> before = snapshot();
        willThrow(StorageException.type(StorageErrorCode.COPY_FAILED)).given(storageService).backUpAndRestore(anyString(), anyString(), anyString(), anyString());
        loginAs(ADMIN);

        assertThatThrownBy(() -> versionUseCase.restoreVersion(resourceSeq, versionSeq, null, ADMIN)).isInstanceOf(ApiRuntimeException.class);

        Mockito.verify(storageService).backUpAndRestore(anyString(), anyString(), anyString(), anyString());
        assertThat(snapshot()).isEqualTo(before);
    }

    /**
     * D6 — 업로드가 PENDING 만료(3분)를 넘기는 사이 다음 저장(B)이 만료 정리로 A 의 메타를 이미 되돌리고 자기 버전을 저장했다면,
     * 뒤늦은 A 의 스토리지 실패 롤백은 B 의 저장을 덮어쓰지 않는다 — 이미 없는 A 의 버전 행 삭제가 0행이라 UPDATE_FAIL 로 롤백 트랜잭션 전체가
     * 되돌려지는 것이 행 수 가드 역할을 한다(10-12 확인, 코드 변경 없음).
     * 같이 드러난 사용량 버그: 만료 정리가 PENDING 행 크기(이전 내용 10)를 돌려줘 A 가 예약한 새 크기(20)와의 차이 10 이 남았다 → 현재 크기를 돌려주게 수정.
     */
    @Test
    @DisplayName("D6 — 만료 정리 뒤 늦은 롤백: B 의 저장(크기·버전 번호·버전 행)을 덮어쓰지 않고, 사용량은 B 만 반영(만료 정리가 A 예약분 전부 반환)")
    void lateRollbackAfterExpiredCleanupKeepsNextSave() {
        createFile();
        AtomicInteger calls = new AtomicInteger();
        willAnswer(inv -> {
            if (calls.incrementAndGet() == 1) {
                // A 업로드 중: A 의 PENDING 을 만료시키고 B 저장을 끝낸 뒤 A 업로드 실패
                jdbc.update("UPDATE tb_resource_version SET register_date = register_date - INTERVAL '10 minutes' WHERE resource_seq = ? AND status <> 'SUCCESS'", resourceSeq);
                webOfficeSave(30L);
                throw StorageException.type(StorageErrorCode.UPLOAD_FAILED);
            }
            return null;
        }).given(storageService).copyAndUpload(any(FileData.class), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> webOfficeSave(20L)).isNotNull();

        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, current_version_number FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        assertThat(resource.get("size_byte")).as("B 의 크기 유지").isEqualTo(30L);
        assertThat(resource.get("current_version_number")).as("B 의 버전 번호 유지").isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT status FROM tb_resource_version WHERE resource_seq = ?", String.class, resourceSeq))
                .as("B 가 남긴 이전 버전 행 1개(SUCCESS), A 의 행은 B 의 만료 정리가 지움").containsExactly("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN))
                .as("A 예약분은 B 의 만료 정리가 돌려줌 — B 만 반영").isEqualTo(1000L + 30L);
    }

    /**
     * D1 — 스토리지가 성공한 뒤의 후처리(사용량 알림) 실패는 저장을 되돌리지 않는다. 후처리는 FileUploadedEvent 비동기 리스너가 맡고 실패를 삼킨다.
     * 구 흐름은 같은 try 안이라 메타를 업로드 전으로 되돌려, 스토리지 원본 키(새 내용)와 메타(이전 크기·버전)가 어긋나고 이전 내용 버전 키가 고아로 남았다.
     */
    @Test
    @DisplayName("D1 — 업로드 성공 후 후처리 실패: 저장 유지(새 버전 행·크기·사용량), 오류 없음")
    void postProcessingFailureKeepsSave() {
        createFile();
        webOfficeSave(20L);
        Long quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED))
                .given(postProcessingOrchestratorRetry).processQuotaIncreaseAndNotify(any());

        webOfficeSave(30L);

        Mockito.verify(postProcessingOrchestratorRetry, Mockito.timeout(5000).times(2)).processQuotaIncreaseAndNotify(any());
        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, current_version_number FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        assertThat(resource.get("size_byte")).isEqualTo(30L);
        assertThat(resource.get("current_version_number")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource_version WHERE resource_seq = ? AND status = 'SUCCESS'", Integer.class, resourceSeq)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN)).isEqualTo(quotaBefore + 30L);
    }

    /** finalize 실패로 크기 20 저장의 새 버전을 PENDING 으로 남기고 만료시킨다 — 반환 [원본 키, 버전 키, PENDING 버전 seq(= 그 저장의 마커)]. */
    private String[] leaveExpiredPendingAfterFinalizeFailure() {
        createFile();
        willThrow(ApiRuntimeException.type(ResourceVersionErrorCode.VERSION_PROCESSING_ERROR))
                .given(resourceVersionRetryService).finalizeAfterStorageUpload(any(), anyInt(), any());
        webOfficeSave(20L);
        willCallRealMethod().given(resourceVersionRetryService).finalizeAfterStorageUpload(any(), anyInt(), any());
        jdbc.update("UPDATE tb_resource_version SET register_date = register_date - INTERVAL '10 minutes' WHERE resource_seq = ? AND status <> 'SUCCESS'", resourceSeq);
        given(storageService.deleteObjectsAsync(any())).willReturn(CompletableFuture.completedFuture(null));
        String resourceKey = jdbc.queryForObject("SELECT resource_save_name FROM tb_resource WHERE resource_seq = ?", String.class, resourceSeq);
        String versionKey = jdbc.queryForObject("SELECT resource_save_name FROM tb_resource_version WHERE resource_seq = ? AND status = 'PENDING'", String.class, resourceSeq);
        Long pendingSeq = jdbc.queryForObject("SELECT resource_version_seq FROM tb_resource_version WHERE resource_seq = ? AND status = 'PENDING'", Long.class, resourceSeq);
        // 20 저장이 스토리지에 넘긴 마커 = 자기 버전 seq
        verify(storageService).copyAndUpload(any(FileData.class), Mockito.eq(resourceKey), Mockito.eq(versionKey), Mockito.eq(String.valueOf(pendingSeq)));
        return new String[]{resourceKey, versionKey, String.valueOf(pendingSeq)};
    }

    /**
     * finalize 항목 — 스토리지 성공 뒤 finalize 만 실패해 남은 PENDING 은, 다음 저장의 만료 정리가 되돌리지 않고 finalize 를 대신 마친다
     * (원본 키의 저장 마커 = 그 PENDING 의 seq = 복사 뒤 업로드까지 끝남). 구 동작은 메타를 이전 크기·버전으로 되돌려 원본 키(새 내용)와 어긋났다.
     */
    @Test
    @DisplayName("finalize — 원본 키 마커가 만료 PENDING 의 seq 면 다음 저장이 SUCCESS 로 마무리: 두 저장 모두 유지(버전 행 2·사용량 +20 +30)")
    void expiredPendingWithCompletedUploadIsFinalized() {
        String[] keys = leaveExpiredPendingAfterFinalizeFailure();
        given(storageService.findLastModified(keys[1])).willReturn(Optional.of(Instant.parse("2026-10-12T00:00:00Z")));
        given(storageService.findSaveMarker(keys[0])).willReturn(Optional.of(keys[2]));

        webOfficeSave(30L);

        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, current_version_number FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        assertThat(resource.get("size_byte")).isEqualTo(30L);
        assertThat(resource.get("current_version_number")).as("버전 1 → 20 저장(2) → 30 저장(3)").isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT size_byte FROM tb_resource_version WHERE resource_seq = ? AND status = 'SUCCESS' ORDER BY version_number", Long.class, resourceSeq))
                .as("이전 내용 10(20 저장이 남김)·20(30 저장이 남김)").containsExactly(10L, 20L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource_version WHERE resource_seq = ? AND status <> 'SUCCESS'", Integer.class, resourceSeq)).isZero();
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN)).isEqualTo(1000L + 20L + 30L);
        assertThat(storageDeletionCount(keys[1])).as("밀려난 버전 키는 삭제 대기열에 없음").isZero();
    }

    /**
     * 리뷰 지적(P2) 회귀 — 직전 저장의 업로드와 이번 백업 복사가 같은 초라 두 Last-Modified 가 같아도, 원본 키 마커가 직전 저장의 것이면 업로드 전 중단이다.
     * 구 판정(원본 수정 시각 ≥ 버전 키)은 이 경우를 완료로 보고 새 크기·사용량을 유지하고 밀려난 버전까지 지웠다.
     */
    @Test
    @DisplayName("finalize — 원본 키 마커가 직전 저장의 것(같은 초 Last-Modified)이면 업로드 전 중단: 되돌리고 남은 버전 키 사본을 지운다")
    void expiredPendingWithoutUploadIsRevertedAndCopyDeleted() {
        String[] keys = leaveExpiredPendingAfterFinalizeFailure();
        Instant sameSecond = Instant.parse("2026-10-12T00:00:00Z");
        given(storageService.findLastModified(keys[1])).willReturn(Optional.of(sameSecond));
        given(storageService.findLastModified(keys[0])).willReturn(Optional.of(sameSecond));
        given(storageService.findSaveMarker(keys[0])).willReturn(Optional.of(String.valueOf(Long.parseLong(keys[2]) - 1)));

        webOfficeSave(30L);

        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, current_version_number FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        assertThat(resource.get("size_byte")).isEqualTo(30L);
        assertThat(resource.get("current_version_number")).as("20 저장은 되돌려짐 — 버전 1 → 30 저장(2)").isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT size_byte FROM tb_resource_version WHERE resource_seq = ? ORDER BY version_number", Long.class, resourceSeq)).containsExactly(10L);
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN)).isEqualTo(1000L + 30L);
        assertThat(storageDeletionCount(keys[1])).as("남은 버전 키 사본이 삭제 대기열에 등록").isEqualTo(1);
    }

    /**
     * D1 — 마무리(finalize: 새 버전 PENDING → SUCCESS, 밀려난 버전 행 삭제) 실패도 즉시 되돌리지 않는다(오케스트레이터가 ERROR 로그만, finalize 자체는 재시도 3회).
     * 남는 것: 새 버전 행이 PENDING, 밀려난 버전 행이 DELETE. 만료(3분) 뒤 다음 저장의 정리가 스토리지로 업로드 완료를 확인해 finalize 를 대신 마친다
     * (10-12 — expiredPendingWithCompletedUploadIsFinalized). 그 전 구현은 무조건 되돌려 원본 키(새 내용)와 메타가 어긋났다.
     */
    @Test
    @DisplayName("D1 — 한도 + finalize 실패: 저장 유지, 새 버전 PENDING·밀려난 버전 DELETE 로 남음, 사용량 = +새 크기 −밀려난 크기")
    void finalizeFailureKeepsSave() {
        createFile();
        webOfficeSave(20L);
        webOfficeSave(40L);
        Long pushedOutSeq = jdbc.queryForObject("SELECT resource_version_seq FROM tb_resource_version WHERE resource_seq = ? AND version_number = 1", Long.class, resourceSeq);
        Long quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        willThrow(ApiRuntimeException.type(ResourceVersionErrorCode.VERSION_PROCESSING_ERROR))
                .given(resourceVersionRetryService).finalizeAfterStorageUpload(any(), anyInt(), any());

        webOfficeSave(80L);

        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, resourceSeq)).isEqualTo(80L);
        assertThat(jdbc.queryForObject("SELECT status FROM tb_resource_version WHERE resource_version_seq = ?", String.class, pushedOutSeq)).isEqualTo("DELETE");
        assertThat(jdbc.queryForObject("SELECT status FROM tb_resource_version WHERE resource_seq = ? ORDER BY resource_version_seq DESC FETCH FIRST 1 ROWS ONLY",
                String.class, resourceSeq)).as("새 버전은 finalize 가 SUCCESS 로 바꾸는데 실패 — PENDING").isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource_version WHERE resource_seq = ? AND status = 'SUCCESS'", Integer.class, resourceSeq)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN)).isEqualTo(quotaBefore + 80L - 10L);
    }
}
