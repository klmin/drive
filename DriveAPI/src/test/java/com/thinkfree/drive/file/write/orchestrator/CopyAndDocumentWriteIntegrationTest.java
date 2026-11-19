package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.file.command.FileCopyCommand;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.document.dto.DocumentCreateDto;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import com.thinkfree.drive.document.service.DocumentService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.resource.entity.Resource;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 파일 복사·문서 생성 → FileWriteOrchestrator(DUPLICATE) 이관 확인(5차 패스, PostgreSQL Testcontainers).
 * 스토리지 실패는 새 리소스 행·사용량을 원복하고, 스토리지 뒤의 후처리(사용량 알림) 실패는 저장을 유지한다(구 복사 흐름은 후처리 실패에도 복사본을 지웠다).
 * <p>
 * 메타 저장이 커밋된 뒤 스토리지를 타므로 트랜잭션 없이 커밋하고 끝에 지운다. 스토리지·후처리·감사 기록은 목. 소유자 = ADMIN(2).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("복사·문서 생성 — DUPLICATE 업로드 세션: 스토리지 실패면 행·사용량 없음, 후처리 실패는 저장 유지")
class CopyAndDocumentWriteIntegrationTest {

    private static final int ADMIN = 2;

    @Autowired
    private FileUseCase fileUseCase;
    @Autowired
    private DocumentService documentService;
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
        testStart = Instant.now().minusSeconds(1);
        // 목 스토리지가 받은 크기를 기억했다가 커밋 전 크기 확인(headContentLength)에 그대로 돌려준다 — 복사는 원본 크기(40)
        java.util.Map<String, Long> stored = new java.util.concurrent.ConcurrentHashMap<>();
        Mockito.doAnswer(inv -> stored.put(inv.getArgument(0), inv.getArgument(3))).when(storageService)
               .putObject(anyString(), anyString(), any(), Mockito.anyLong());
        Mockito.doAnswer(inv -> stored.put(inv.getArgument(1), 40L)).when(storageService).copy(anyString(), anyString());
        given(storageService.headContentLength(anyString())).willAnswer(inv -> stored.getOrDefault((String) inv.getArgument(0), -1L));
    }

    private Instant testStart;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key IN (SELECT storage_key FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?)",
                ADMIN, java.sql.Timestamp.from(testStart));
        jdbc.update("DELETE FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?", ADMIN, java.sql.Timestamp.from(testStart));
        jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 복사 원본 — 크기 40, 루트 */
    private Resource createSource() {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("copy-src.txt").originalFileName("copy-src.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(40L)
                .versionRegisterDate(Instant.parse("2026-01-01T00:00:00Z")).registerUserSeq(ADMIN).build();
        // save 뒤 저장 이름은 변경 감지 — 트랜잭션 안에서(밖이면 save 뒤 분리 상태라 UPDATE 가 안 나감, W10)
        transactionTemplate.executeWithoutResult(s -> {
            resourceRepository.save(r);
            r.initResourceSaveName();
        });

        return r;
    }

    private long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
    }

    private int newRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", Integer.class, ADMIN, maxSeqBefore);
    }

    @Test
    @DisplayName("복사 스토리지 실패 — 복사본 행 삭제·사용량 원복, StorageException 전파, 원본 키 → 새 키 복사 호출")
    void copyStorageFailureRollsBack() {
        Resource source = createSource();
        willThrow(StorageException.type(StorageErrorCode.COPY_FAILED)).given(storageService).copy(anyString(), anyString());

        assertThatThrownBy(() -> fileUseCase.copy(source.getResourceSeq(), new FileCopyCommand(null, null, null), ADMIN))
                .isInstanceOf(StorageException.class);

        verify(storageService).copy(eq(source.getResourceSaveName()), anyString());
        assertThat(newRows()).as("원본만 남음").isEqualTo(1);
        assertThat(quota()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("복사 후 후처리 실패 — 복사본 유지·사용량 +원본 크기, 오류 없음(구 흐름은 복사본을 지웠다)")
    void copyPostProcessingFailureKeepsCopy() {
        Resource source = createSource();
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED))
                .given(postProcessingOrchestratorRetry).processQuotaIncreaseAndNotify(any());

        var info = fileUseCase.copy(source.getResourceSeq(), new FileCopyCommand(null, null, null), ADMIN);

        verify(postProcessingOrchestratorRetry, Mockito.timeout(5000)).processQuotaIncreaseAndNotify(any());
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, info.resourceSeq())).isEqualTo(40L);
        assertThat(newRows()).as("원본 + 복사본").isEqualTo(2);
        assertThat(quota()).isEqualTo(1040L);
    }

    @Test
    @DisplayName("문서 생성 스토리지 실패 — 새 문서 행 없음·사용량 원복, StorageException 전파, 템플릿 스트림 업로드 호출")
    void documentStorageFailureRollsBack() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).putObject(anyString(), anyString(), any(), Mockito.anyLong());

        assertThatThrownBy(() -> documentService.create(DocumentCreateDto.builder().userSeq(ADMIN).ownerSeq(ADMIN).templateType(DocumentTemplateType.WORD).build()))
                .isInstanceOf(StorageException.class);

        verify(storageService).putObject(anyString(), anyString(), any(java.io.InputStream.class), Mockito.anyLong());
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("문서 생성 후 후처리 실패 — 문서 유지·사용량 +템플릿 크기, 오류 없음(구 흐름은 오류 응답)")
    void documentPostProcessingFailureKeepsDocument() {
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED))
                .given(postProcessingOrchestratorRetry).processQuotaIncreaseAndNotify(any());

        var result = documentService.create(DocumentCreateDto.builder().userSeq(ADMIN).ownerSeq(ADMIN).templateType(DocumentTemplateType.WORD).build());

        verify(postProcessingOrchestratorRetry, Mockito.timeout(5000)).processQuotaIncreaseAndNotify(any());
        assertThat(newRows()).isEqualTo(1);
        Long size = jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", Long.class, ADMIN, maxSeqBefore);
        assertThat(size).isPositive();
        assertThat(quota()).isEqualTo(1000L + size);
    }
}
