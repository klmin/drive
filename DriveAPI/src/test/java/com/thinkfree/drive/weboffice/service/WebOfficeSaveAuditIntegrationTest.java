package com.thinkfree.drive.weboffice.service;

import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveDto;
import org.junit.jupiter.api.AfterEach;
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

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 웹오피스 저장(DefaultWebOfficeService.save) 감사 확인(10-07 작업 C-4) — 웹오피스 어댑터 호출은 시큐리티 컨텍스트가 없으므로
 * 버전 등록자(tb_resource_version.register_user_seq)와 리소스 수정자(tb_resource.update_user_seq)가 경로의 loginUserSeq 로 들어가야 한다.
 * <p>
 * 저장 경로가 {@code REQUIRES_NEW}(ResourceRetryService.saveWithPolicy)라 롤백형 테스트로는 앞서 넣은 리소스가 보이지 않는다 → 트랜잭션 없이 커밋하고 끝에 지운다.
 * 스토리지·후처리(알림)·감사 로그 기록은 목으로 막는다. 소유자 = ADMIN(2), 편집자(loginUserSeq) = SUPER_ADMIN(1).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("웹오피스 저장 — 시큐리티 없이 버전 등록자·리소스 수정자 = loginUserSeq")
class WebOfficeSaveAuditIntegrationTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private WebOfficeService webOfficeService;
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

    private Long resourceSeq;
    private Long quotaBefore;
    private Long totalQuotaBefore;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        if (resourceSeq != null) {
            jdbc.update("DELETE FROM tb_resource_version WHERE resource_seq = ?", resourceSeq);
            jdbc.update("DELETE FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        }
        if (quotaBefore != null) {
            jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
        }
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @Test
    @DisplayName("save — 새 버전 이력 register_user_seq = loginUserSeq, 리소스 update_user_seq·latest_owner_seq = loginUserSeq, 버전 번호 +1·크기 갱신")
    void saveUsesLoginUserSeqWithoutSecurity() {
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 0, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
        loginAs(ADMIN);
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("weboffice-audit.docx").originalFileName("weboffice-audit.docx").copyNumber(0).ext("docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document").sizeByte(10L)
                .versionRegisterDate(Instant.now()).registerUserSeq(ADMIN).build();
        // save 뒤 저장 이름은 변경 감지 — 트랜잭션 안에서(밖이면 save 뒤 분리 상태라 UPDATE 가 안 나감, W10)
        transactionTemplate.executeWithoutResult(s -> {
            resourceRepository.save(r);
            r.initResourceSaveName();
        });
        resourceSeq = r.getResourceSeq();

        SecurityContextHolder.clearContext();

        given(storageService.headContentLength(org.mockito.ArgumentMatchers.anyString())).willReturn(30L);
        webOfficeService.save(WebOfficeSaveDto.builder()
                .loginUserSeq(SUPER_ADMIN).userSeq(ADMIN).resourceSeq(resourceSeq).size(30L)
                .inputStream(new ByteArrayInputStream(new byte[30])).build());

        Map<String, Object> resource = jdbc.queryForMap(
                "SELECT current_version_number, size_byte, latest_owner_seq, update_user_seq, update_date FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        assertThat(resource.get("current_version_number")).isEqualTo(2);
        assertThat(resource.get("size_byte")).isEqualTo(30L);
        assertThat(resource.get("latest_owner_seq")).isEqualTo(SUPER_ADMIN);
        assertThat(resource.get("update_user_seq")).as("리소스 수정자 = loginUserSeq").isEqualTo(SUPER_ADMIN);
        assertThat((Timestamp) resource.get("update_date")).isNotNull();

        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT version_number, owner_seq, size_byte, status, register_user_seq FROM tb_resource_version WHERE resource_seq = ?", resourceSeq);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("register_user_seq")).as("버전 등록자 = loginUserSeq").isEqualTo(SUPER_ADMIN);
        assertThat(versions.get(0).get("owner_seq")).as("이력은 이전 최신 소유자").isEqualTo(ADMIN);
        assertThat(versions.get(0).get("version_number")).isEqualTo(1);
        assertThat(versions.get(0).get("size_byte")).isEqualTo(10L);
        assertThat(versions.get(0).get("status")).isEqualTo("SUCCESS");
    }
}
