package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
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
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.service.StorageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 파일 업로드 OVERWRITE·DUPLICATE → FileWriteOrchestrator 전 구간 확인(5차 패스, PostgreSQL Testcontainers).
 * 메타 저장(사용량 예약 포함 한 트랜잭션) → 스토리지 → 스토리지 실패만 원복, 후처리(사용량 알림) 실패는 저장 유지, 용량 부족은 재시도 없이 IS_FULL·알림 1회.
 * <p>
 * 메타 저장이 커밋된 뒤 스토리지를 타므로 트랜잭션 없이 커밋하고 끝에 지운다. 스토리지·후처리·알림·감사 기록은 목. 소유자 = ADMIN(2).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("업로드 OVERWRITE·DUPLICATE — 스토리지 실패만 원복, 후처리 실패는 저장 유지, 용량 부족은 IS_FULL 1회")
class UploadWriteIntegrationTest {

    private static final int ADMIN = 2;

    @Autowired
    private FileUseCase fileUseCase;
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

    private Long maxSeqBefore;
    private Long quotaBefore;
    private Long totalQuotaBefore;

    @BeforeEach
    void setUp() {
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
        loginAs(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 덮어쓸 파일 — 크기 100, 루트, 수정자·수정일은 알려진 과거 값 */
    private Resource createFile() {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("ow-" + System.nanoTime() + ".txt").originalFileName("ow.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(100L)
                .versionRegisterDate(Instant.parse("2026-01-01T00:00:00Z")).registerUserSeq(ADMIN).build();
        // save 뒤 저장 이름은 변경 감지 — 트랜잭션 안에서(밖이면 save 뒤 분리 상태라 UPDATE 가 안 나감, W10)
        transactionTemplate.executeWithoutResult(s -> {
            resourceRepository.save(r);
            r.initResourceSaveName();
        });

        jdbc.update("UPDATE tb_resource SET update_user_seq = 1, update_date = TIMESTAMPTZ '2026-02-02 02:02:02+00' WHERE resource_seq = ?", r.getResourceSeq());
        return r;
    }

    private Map<String, Object> snapshot(Long resourceSeq) {
        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, update_user_seq, update_date FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        return Map.of("resource", resource, "quota", quota());
    }

    private long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
    }

    private int newRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", Integer.class, ADMIN, maxSeqBefore);
    }

    private static MockMultipartFile file(int size) {
        return new MockMultipartFile("file", "up.txt", "text/plain", new byte[size]);
    }

    private void overwrite(Long resourceSeq, int size) {
        fileUseCase.upload(new FileUploadCommand(null, null, resourceSeq, file(size), ResourceSaveType.OVERWRITE), ADMIN, false, false);
    }

    @Test
    @DisplayName("OVERWRITE 성공 — 크기 100 → 300, 사용량 +200, 같은 저장 키에 업로드")
    void overwriteSuccess() {
        Resource r = createFile();

        overwrite(r.getResourceSeq(), 300);

        verify(storageService).upload(any(MultipartFile.class), eq(r.getResourceSaveName()));
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq())).isEqualTo(300L);
        assertThat(quota()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("OVERWRITE 스토리지 실패 — 크기·수정자·수정일·사용량 덮어쓰기 전으로, StorageException 전파")
    void overwriteStorageFailureRollsBack() {
        Resource r = createFile();
        Map<String, Object> before = snapshot(r.getResourceSeq());
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).upload(any(MultipartFile.class), anyString());

        assertThatThrownBy(() -> overwrite(r.getResourceSeq(), 300)).isInstanceOf(StorageException.class);

        verify(storageService).upload(any(MultipartFile.class), eq(r.getResourceSaveName()));
        assertThat(snapshot(r.getResourceSeq())).isEqualTo(before);
    }

    @Test
    @DisplayName("OVERWRITE 후처리 실패 — 저장 유지(크기 300·사용량 +200), 오류 없음")
    void overwritePostProcessingFailureKeepsSave() {
        Resource r = createFile();
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED))
                .given(postProcessingOrchestratorRetry).processQuotaIncreaseAndNotify(any());

        overwrite(r.getResourceSeq(), 300);

        verify(postProcessingOrchestratorRetry, Mockito.timeout(5000)).processQuotaIncreaseAndNotify(any());
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq())).isEqualTo(300L);
        assertThat(quota()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("OVERWRITE 용량 부족 — IS_FULL 그대로 전파, 용량 초과 알림 1회, 메타·사용량 변화 없음, 스토리지 호출 없음")
    void overwriteQuotaFullIsNotRetried() {
        Resource r = createFile();
        jdbc.update("UPDATE tb_user SET use_quota_byte = total_quota_byte WHERE user_seq = ?", ADMIN);
        Map<String, Object> before = snapshot(r.getResourceSeq());

        assertThatThrownBy(() -> overwrite(r.getResourceSeq(), 300))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(notificationRetryService, Mockito.times(1)).quotaExceededNotify(any());
        verify(storageService, never()).upload(any(MultipartFile.class), anyString());
        assertThat(snapshot(r.getResourceSeq())).isEqualTo(before);
    }

    @Test
    @DisplayName("DUPLICATE 업로드 스토리지 실패 — 새 행 없음·사용량 원복, StorageException 전파")
    void duplicateStorageFailureRollsBack() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).upload(any(MultipartFile.class), anyString());

        assertThatThrownBy(() -> fileUseCase.upload(new FileUploadCommand(null, null, null, file(50), ResourceSaveType.DUPLICATE), ADMIN, false, false))
                .isInstanceOf(StorageException.class);

        verify(storageService).upload(any(MultipartFile.class), anyString());
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("DUPLICATE 업로드 용량 부족 — IS_FULL 그대로 전파, 용량 초과 알림 1회, 새 행 없음, 스토리지 호출 없음")
    void duplicateQuotaFullIsNotRetried() {
        jdbc.update("UPDATE tb_user SET use_quota_byte = total_quota_byte WHERE user_seq = ?", ADMIN);
        long full = quota();

        assertThatThrownBy(() -> fileUseCase.upload(new FileUploadCommand(null, null, null, file(50), ResourceSaveType.DUPLICATE), ADMIN, false, false))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(notificationRetryService, Mockito.times(1)).quotaExceededNotify(any());
        verify(storageService, never()).upload(any(MultipartFile.class), anyString());
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(full);
    }
}
