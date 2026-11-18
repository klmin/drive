package com.thinkfree.drive.global.bootstrap;

import com.thinkfree.drive.api.v1.admin.systemconfig.request.OfficeUpdateRequest;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.systemconfig.data.OfficeData;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("InitialSetupRunner — 하나뿐인 테넌트에, 값이 주입됐고 DB 에 없을 때만 라이선스·office 를 넣는다")
class InitialSetupRunnerTest {

    private static final int TENANT = 7;
    private static final String SECRET = "s".repeat(32);

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @TempDir
    Path tempDir;

    private final TenantFindService tenantFindService = mock(TenantFindService.class);
    private final LicenseService licenseService = mock(LicenseService.class);
    private final LicenseQueryRepository licenseQueryRepository = mock(LicenseQueryRepository.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final SystemConfigFindService systemConfigFindService = mock(SystemConfigFindService.class);
    private final SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);

    @BeforeEach
    void defaults() {
        when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(TENANT);
        when(systemConfigFindService.findOfficeConfig(TENANT)).thenReturn(OfficeData.builder().officeDomain("").build());
        when(systemConfigRepository.countByTenantAndKeys(eq(TENANT), any())).thenReturn(0);
        when(systemConfigFindService.isOfficeServiceManaged(TENANT)).thenReturn(false);
    }

    private InitialSetupRunner runner(String licenseFile, InitialSetupProperty.Office office) {
        return new InitialSetupRunner(new InitialSetupProperty(licenseFile, office), tenantFindService,
                licenseService, licenseQueryRepository, systemConfigService, systemConfigFindService, systemConfigRepository, validator);
    }

    private static InitialSetupProperty.Office noOffice() {
        return new InitialSetupProperty.Office(null, null, "ticket", null, null, "onPremise");
    }

    private static InitialSetupProperty.Office fullOffice() {
        return new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", "token", SECRET, "onPremise");
    }

    @Test
    @DisplayName("아무것도 주입하지 않으면 테넌트 조회도 하지 않는다")
    void nothingInjected() {
        runner(null, noOffice()).run(null);

        verifyNoInteractions(tenantFindService, licenseService, licenseQueryRepository, systemConfigService);
    }

    @Test
    @DisplayName("활성 자체관리 테넌트가 하나가 아니면(null) 라이선스·office 모두 건너뛴다")
    void skipsWhenTenantNotSingle() throws Exception {
        when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(null);
        Path file = Files.write(tempDir.resolve("license.tfl"), new byte[]{1});

        runner(file.toString(), fullOffice()).run(null);

        verifyNoInteractions(licenseService, licenseQueryRepository, systemConfigService);
    }

    @Test
    @DisplayName("찾은 테넌트에 라이선스가 없으면 파일 바이트 그대로 업로드와 같은 registerLicense 로 등록")
    void registersLicenseToFoundTenant() throws Exception {
        Path file = Files.write(tempDir.resolve("license.tfl"), new byte[]{1, 2, 3});
        when(licenseQueryRepository.findLicense(TENANT)).thenReturn(null);

        runner(file.toString(), noOffice()).run(null);

        verify(licenseService).registerLicense(TENANT, new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("라이선스가 이미 있으면 등록하지 않는다")
    void keepsExistingLicense() throws Exception {
        Path file = Files.write(tempDir.resolve("license.tfl"), new byte[]{1});
        when(licenseQueryRepository.findLicense(TENANT)).thenReturn(Map.of("tenant_seq", TENANT));

        runner(file.toString(), noOffice()).run(null);

        verify(licenseService, never()).registerLicense(anyInt(), any());
    }

    @Test
    @DisplayName("파일이 없으면 건너뛰고, 라이선스 등록이 실패해도 office 설정은 이어서 한다(기동도 안 막음)")
    void licenseFailureDoesNotStopOffice() throws Exception {
        runner(tempDir.resolve("none.tfl").toString(), noOffice()).run(null);
        verify(licenseService, never()).registerLicense(anyInt(), any());

        Path file = Files.write(tempDir.resolve("bad.tfl"), new byte[]{9});
        doThrow(new IllegalStateException("bad")).when(licenseService).registerLicense(anyInt(), any());

        runner(file.toString(), fullOffice()).run(null);

        verify(systemConfigService).upsertOffice(eq(TENANT), eq(DriveConstants.SUPER_ADMIN_USER_SEQ), any(OfficeUpdateRequest.class));
    }

    @Test
    @DisplayName("office 미설정이고 값이 화면 검증을 통과하면 6개 항목을 통째로 저장")
    void savesValidOffice() {
        runner(null, new InitialSetupProperty.Office(" https://tfo.example.com ", " nnew-drive ", "ticket", "token", SECRET, "onPremise")).run(null);

        ArgumentCaptor<OfficeUpdateRequest> captor = ArgumentCaptor.forClass(OfficeUpdateRequest.class);
        verify(systemConfigService).upsertOffice(eq(TENANT), eq(DriveConstants.SUPER_ADMIN_USER_SEQ), captor.capture());
        OfficeUpdateRequest request = captor.getValue();
        assertThat(request.officeDomain()).isEqualTo("https://tfo.example.com");
        assertThat(request.officeHttpStorageAdapterName()).isEqualTo("nnew-drive");
        assertThat(request.officeConnectionType()).isEqualTo("onPremise");
        assertThat(request.officeAuthMode()).isEqualTo("ticket");
        assertThat(request.officeTicketServiceToken()).isEqualTo("token");
        assertThat(request.officeHttpStorageRequestJwtSecret()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("화면 검증을 통과하지 못하면(토큰·서명키 없음, 짧은 서명키, 잘못된 주소) 아무것도 저장하지 않는다")
    void rejectsInvalidOffice() {
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", null, null, "onPremise")).run(null);
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", "token", "short", "onPremise")).run(null);
        runner(null, new InitialSetupProperty.Office("ftp://tfo.example.com", "nnew-drive", "ticket", "token", SECRET, "onPremise")).run(null);
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "bad name!", "ticket", "token", SECRET, "onPremise")).run(null);

        verify(systemConfigService, never()).upsertOffice(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("연결 방식은 주입값 그대로 넘기고(none 포함), 허용값 밖이면 화면 검증에 걸려 저장하지 않는다")
    void passesConnectionTypeThrough() {
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", "token", SECRET, " none ")).run(null);

        ArgumentCaptor<OfficeUpdateRequest> captor = ArgumentCaptor.forClass(OfficeUpdateRequest.class);
        verify(systemConfigService).upsertOffice(eq(TENANT), eq(DriveConstants.SUPER_ADMIN_USER_SEQ), captor.capture());
        assertThat(captor.getValue().officeConnectionType()).isEqualTo("none");

        clearInvocations(systemConfigService);
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", "token", SECRET, "cloud")).run(null);
        runner(null, new InitialSetupProperty.Office("https://tfo.example.com", "nnew-drive", "ticket", "token", SECRET, null)).run(null);

        verify(systemConfigService, never()).upsertOffice(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("이미 office 설정이 있으면(주소 있음 / 연결 방식 저장됨 / 서비스 관리) 덮어쓰지 않는다")
    void keepsConfiguredOffice() {
        when(systemConfigFindService.findOfficeConfig(TENANT)).thenReturn(OfficeData.builder().officeDomain("https://already.example.com").build());
        runner(null, fullOffice()).run(null);

        when(systemConfigFindService.findOfficeConfig(TENANT)).thenReturn(OfficeData.builder().officeDomain("").build());
        when(systemConfigRepository.countByTenantAndKeys(TENANT, List.of("office_connection_type"))).thenReturn(1);
        runner(null, fullOffice()).run(null);

        when(systemConfigRepository.countByTenantAndKeys(TENANT, List.of("office_connection_type"))).thenReturn(0);
        when(systemConfigFindService.isOfficeServiceManaged(TENANT)).thenReturn(true);
        runner(null, fullOffice()).run(null);

        verify(systemConfigService, never()).upsertOffice(anyInt(), anyInt(), any());
    }
}
