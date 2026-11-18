package com.thinkfree.drive.file.write.service.impl;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.global.time.DriveTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 덮어쓰기 → 스토리지 실패 보상(rollback) 회귀 — 크기·수정자·수정일·사용량이 덮어쓰기 전으로 돌아가는지(PostgreSQL Testcontainers).
 * 10-08 이전에는 되돌릴 크기에 새 크기(result.sizeByte)를 넘겨 size_byte 가 복원되지 않았다.
 */
@DisplayName("OverwriteFileWriteService.rollback — 덮어쓰기 전 크기·감사 값·사용량 복원")
class OverwriteFileWriteServiceRollbackTest extends RepositoryTest {

    private static final int ADMIN = 2;

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private OverwriteFileWriteService overwriteFileWriteService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void login() {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private <T> T col(long resourceSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource WHERE resource_seq = ?", type, resourceSeq);
    }

    @Test
    @DisplayName("100 → 300 덮어쓰기 후 rollback: size_byte 100, update_user_seq·update_date 덮어쓰기 전 값, 사용량 -200")
    void rollbackRestoresPreviousSize() {
        Resource f = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("ow.txt").originalFileName("ow.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(100L)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(ADMIN).build();
        resourceRepository.save(f);
        f.initResourceSaveName();
        resourceRepository.flush();
        long seq = f.getResourceSeq();
        // 덮어쓰기 전 감사 값을 알려진 값으로(다른 사용자·과거 시각)
        jdbc.update("UPDATE tb_resource SET update_user_seq = 1, update_date = TIMESTAMPTZ '2026-02-02 02:02:02+00' WHERE resource_seq = ?", seq);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000 WHERE user_seq = ?", ADMIN);
        Object beforeUpdateDate = col(seq, "update_date", Object.class);

        ResourceSaveResult result = resourceService.updateWithOverwrite(FileUploadDto.builder()
                .resourceSeq(seq).userSeq(ADMIN).fileSize(300L).resourceSaveType(ResourceSaveType.OVERWRITE).build());
        assertThat(col(seq, "size_byte", Long.class)).isEqualTo(300L);
        assertThat(col(seq, "update_user_seq", Integer.class)).isEqualTo(ADMIN);
        assertThat(result.sizeByte()).as("덮어쓴 새 크기").isEqualTo(300L);
        assertThat(result.restoreSizeByte()).as("덮어쓰기 전 크기").isEqualTo(100L);
        assertThat(result.rollbackSizeByte()).as("사용량 증감분").isEqualTo(200L);

        overwriteFileWriteService.rollback(result);

        assertThat(col(seq, "size_byte", Long.class)).as("크기 복원").isEqualTo(100L);
        assertThat(col(seq, "update_user_seq", Integer.class)).as("수정자 복원").isEqualTo(1);
        assertThat(col(seq, "update_date", Object.class)).as("수정일 복원").isEqualTo(beforeUpdateDate);
        assertThat(jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN)).as("사용량 -200").isEqualTo(800L);
    }
}
