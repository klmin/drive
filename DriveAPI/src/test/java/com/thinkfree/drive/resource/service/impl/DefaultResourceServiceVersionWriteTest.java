package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

/**
 * W10 버전 작업 경로(DefaultResourceService) — DB 값·결과 값 회귀(PostgreSQL Testcontainers, 트랜잭션 롤백).
 * <ul>
 *   <li>버전 잠금 = {@code @Lock} 생성자 식 DTO(구 JdbcClient 부분 엔티티) — 없는/삭제된 행은 NOT_FOUND</li>
 *   <li>버전 저장 결과 = 저장 전 값(롤백 기준) — 구 revertToPreviousVersionMetadata(메모리 되돌리기) 없이, 이벤트 시각 = 버전 등록 시각</li>
 *   <li>버전 삭제·전체 삭제 이벤트 시각 = Service 현재 시각(구 감사 write-back), 덮어쓰기 결과 감사 값 = 실제로 쓴 값(되돌리기 조건)</li>
 * </ul>
 * DB 확인 전에 flush — 변경 감지 경로는 커밋 때 UPDATE 가 나가므로(통합 테스트는 롤백이라 직접 flush).
 */
@DisplayName("DefaultResourceService 버전 작업 — 잠금 DTO·저장 전 값 결과·이벤트 시각·덮어쓰기 감사 값")
class DefaultResourceServiceVersionWriteTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;
    private static final Instant PAST_UPDATE = Instant.parse("2026-02-02T02:02:02Z");
    private static final Instant PAST_VERSION = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long resourceSeq;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("w10.docx").originalFileName("w10.docx").copyNumber(0).ext("docx").contentType("application/octet-stream").sizeByte(10L)
                .versionRegisterDate(PAST_VERSION).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        resourceSeq = r.getResourceSeq();
        entityManager.flush();
        // 저장 전 감사 값을 알려진 값으로(다른 사용자·과거 시각)
        jdbc.update("UPDATE tb_resource SET update_user_seq = ?, update_date = ? WHERE resource_seq = ?", SUPER_ADMIN, Timestamp.from(PAST_UPDATE), resourceSeq);
        // 서비스는 다른 요청(새 트랜잭션)에서 로드 — 저장 뒤 관리 상태로 남은 인스턴스(옛 감사 값)를 비운다(W10)
        entityManager.clear();
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private <T> T col(String column, Class<T> type) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource WHERE resource_seq = ?", type, resourceSeq);
    }

    private FileUploadDto versionUpload(long size) {
        return FileUploadDto.builder().resourceSeq(resourceSeq).userSeq(ADMIN).ownerSeq(ADMIN).fileSize(size)
                .versionRegisterUserSeq(ADMIN).resourceSaveType(ResourceSaveType.VERSION).build();
    }

    @Test
    @DisplayName("lockResourceForVersioning — 미삭제 행을 값으로(전 컬럼), 없는 행·삭제된 행은 NOT_FOUND")
    void lockResourceForVersioning_returnsDetail() {
        var locked = resourceService.lockResourceForVersioning(resourceSeq, ADMIN);
        assertThat(locked.getResourceSeq()).isEqualTo(resourceSeq);
        assertThat(locked.getResourceSaveName()).isEqualTo(ADMIN + "/" + resourceSeq);
        assertThat(locked.getSizeByte()).isEqualTo(10L);
        assertThat(locked.getOwnerSeq()).as("부분 엔티티가 아니라 전 컬럼 DTO").isEqualTo(ADMIN);

        assertThatThrownBy(() -> resourceService.lockResourceForVersioning(resourceSeq, SUPER_ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(ResourceErrorCode.NOT_FOUND));
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", resourceSeq);
        assertThatThrownBy(() -> resourceService.lockResourceForVersioning(resourceSeq, ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(ResourceErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("updateWithVersioning — DB 는 새 값(크기·버전·최종 소유자·수정자), 결과는 저장 전 값(롤백 기준), 이벤트 시각 = 버전 등록 시각")
    void updateWithVersioning_resultHoldsPreviousValues() {
        var result = resourceService.updateWithVersioning(versionUpload(20L));

        assertThat(col("size_byte", Long.class)).isEqualTo(20L);
        assertThat(col("current_version_number", Integer.class)).isEqualTo(2);
        assertThat(col("update_user_seq", Integer.class)).as("시큐리티 사용자").isEqualTo(ADMIN);

        assertThat(result.sizeByte()).as("저장 전 크기").isEqualTo(10L);
        assertThat(result.updateUserSeq()).as("저장 전 수정자").isEqualTo(SUPER_ADMIN);
        assertThat(result.updateDate()).as("저장 전 수정일").isEqualTo(PAST_UPDATE);
        assertThat(result.versionRegisterDate()).as("저장 전 버전 등록일").isEqualTo(PAST_VERSION);
        assertThat(result.currentVersionNumber()).as("저장 전 버전 번호").isEqualTo(1);
        assertThat(result.rollbackSizeByte()).as("새 크기").isEqualTo(20L);
        Instant versionRegisterDate = col("version_register_date", Timestamp.class).toInstant();
        // PG timestamptz 는 마이크로초로 반올림 저장 — 1µs 이내
        assertThat(result.occurredDatetime()).as("이벤트 시각 = 새 버전 등록 시각").isCloseTo(versionRegisterDate, within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("deleteVersion — 현재 버전 번호 -1, 이벤트 시각 = 현재 시각 / deleteAllVersions — 버전 번호 기본값, 이벤트 시각 = 현재 시각")
    void deleteVersionAndAll_occurredNow() {
        resourceService.updateWithVersioning(versionUpload(20L));
        Long versionSeq = jdbc.queryForObject("SELECT resource_version_seq FROM tb_resource_version WHERE resource_seq = ?", Long.class, resourceSeq);
        jdbc.update("UPDATE tb_resource_version SET status = 'SUCCESS' WHERE resource_version_seq = ?", versionSeq);
        assertThat(col("current_version_number", Integer.class)).isEqualTo(2);

        Instant before = Instant.now();
        var deleted = resourceService.deleteVersion(resourceSeq, versionSeq, ADMIN);
        assertThat(col("current_version_number", Integer.class)).isEqualTo(1);
        assertThat(deleted.occurredDatetime()).isBetween(before, Instant.now());

        jdbc.update("UPDATE tb_resource SET current_version_number = 5 WHERE resource_seq = ?", resourceSeq);
        entityManager.clear();
        Instant beforeAll = Instant.now();
        var all = resourceService.deleteAllVersions(resourceSeq, ADMIN);
        assertThat(col("current_version_number", Integer.class)).isEqualTo(1);
        assertThat(all.occurredDatetime()).isBetween(beforeAll, Instant.now());
    }

    @Test
    @DisplayName("updateWithOverwrite — 결과의 수정자·수정일 = DB 에 쓴 값(되돌리기 조건), restore* = 덮어쓰기 전 값, 이벤트 시각 = 쓴 수정일")
    void updateWithOverwrite_resultHoldsWrittenStamp() {
        var result = resourceService.updateWithOverwrite(FileUploadDto.builder().resourceSeq(resourceSeq).userSeq(ADMIN).fileSize(30L)
                .resourceSaveType(ResourceSaveType.OVERWRITE).build());

        assertThat(col("size_byte", Long.class)).isEqualTo(30L);
        assertThat(result.sizeByte()).isEqualTo(30L);
        assertThat(result.updateUserSeq()).isEqualTo(col("update_user_seq", Integer.class)).isEqualTo(ADMIN);
        assertThat(result.updateDate()).isEqualTo(col("update_date", Timestamp.class).toInstant());
        assertThat(result.occurredDatetime()).isEqualTo(result.updateDate());
        assertThat(result.restoreSizeByte()).isEqualTo(10L);
        assertThat(result.restoreUpdateUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(result.restoreUpdateDate()).isEqualTo(PAST_UPDATE);
        assertThat(result.rollbackSizeByte()).isEqualTo(20L);
    }
}
