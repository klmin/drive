package com.thinkfree.drive.application.admin;

import com.thinkfree.drive.application.admin.command.AdminImportCsvCommand;
import com.thinkfree.drive.application.admin.user.AdminUserImportUseCase;
import com.thinkfree.drive.application.admin.info.AdminImportCsvInfo;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CSV 일괄 등록 중 사용자 INSERT 실패(좌석 한도 경합 등 — insertAll 의 INSERT_FAIL) 시 실패 CSV 를 돌려줘야 한다.
 * <p>
 * 구 동작: 안쪽 트랜잭션 서비스(DefaultAdminUserService, 클래스 @Transactional — 바깥에 합류)를 예외가 지나며 트랜잭션을 rollback-only 로 표시 →
 * importCsv 가 예외를 잡아 Fail 을 반환해도 커밋 시점에 UnexpectedRollbackException(사용자에게 500).
 * 실제 트랜잭션 경계를 타야 재현되므로 롤백형이 아닌 @SpringBootTest. 사용자 행이 생기지 않으므로 정리할 데이터 없음(남으면 지운다).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CSV 일괄 등록 — 사용자 INSERT 실패 시 500 이 아니라 실패 CSV, 전부 롤백")
class AdminImportCsvRollbackTest {

    private static final int TENANT = 1;
    private static final int ADMIN = 2;
    private static final String USER_ID = "csv-rollback-probe@test.com";

    @Autowired
    private AdminUserImportUseCase adminUserImportUseCase;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private UserRepository userRepository;
    @MockitoBean
    private SeatLicenseService seatLicenseService;
    @MockitoBean
    private QuotaService quotaService;
    @MockitoBean
    private LicenseService licenseService;
    @MockitoBean // 테넌트 총 용량 판정은 이 테스트 대상(INSERT 실패 롤백) 밖 — 통과시킨다
    private TenantUserQuotaGuard tenantUserQuotaGuard;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(quotaService.canIncrease(anyLong())).willReturn(true);
        given(tenantUserQuotaGuard.isAssignable(anyInt(), anyLong())).willReturn(true);
        given(licenseService.getSeatPolicy(anyInt())).willReturn(new LicenseService.SeatPolicy(true, 1));
        doThrow(ApiRuntimeException.type(UserErrorCode.INSERT_FAIL)).when(userRepository).insertAll(anyList(), anyBoolean(), anyInt());
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbc.update("DELETE FROM tb_user WHERE user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("insertAll INSERT_FAIL → AdminImportCsvInfo.Fail 반환(UnexpectedRollbackException 없음), 사용자 행 없음")
    void insertFailureReturnsFailCsv() {
        String csv = "﻿UserID,Password,Name,StorageLimit(GB),CountryCode,PhoneNumber\n" + USER_ID + ",Passw0rd!x,csvprobe,1,,\n";
        var file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        AdminImportCsvInfo result = adminUserImportUseCase.importCsv(new AdminImportCsvCommand(file, false), ADMIN);

        assertThat(result).isInstanceOf(AdminImportCsvInfo.Fail.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE user_id = ?", Integer.class, USER_ID)).isZero();
    }

    @Test
    @DisplayName("파일 안에 같은 UserID 가 두 번 — DB 에 없어도 INSERT 전에 실패 CSV(유니크 위반 500 아님)")
    void duplicateInFileReturnsFailCsv() {
        String csv = "﻿UserID,Password,Name,StorageLimit(GB),CountryCode,PhoneNumber\n"
                + USER_ID + ",Passw0rd!x,csvprobe,1,,\n" + USER_ID + ",Passw0rd!x,csvprobe2,1,,\n";
        var file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        AdminImportCsvInfo result = adminUserImportUseCase.importCsv(new AdminImportCsvCommand(file, false), ADMIN);

        assertThat(result).isInstanceOf(AdminImportCsvInfo.Fail.class);
        verify(userRepository, never()).insertAll(anyList(), anyBoolean(), anyInt());
    }
}
