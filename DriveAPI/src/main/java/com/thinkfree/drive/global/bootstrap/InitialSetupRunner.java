package com.thinkfree.drive.global.bootstrap;

import com.thinkfree.drive.api.v1.admin.systemconfig.request.OfficeUpdateRequest;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 설치 직후 초기 설정 — 구 설치기(도커)가 SQL 로 넣던 라이선스·office 연결 값을 외부 주입({@link InitialSetupProperty})으로 받아 앱이 넣는다.
 * <p>
 * 순서: Flyway 마이그레이션(컨텍스트 초기화 중) → 이 러너 → {@code LicenseInitializer}(캐시 warm-up·온라인 인증).
 * <ul>
 *   <li>대상 테넌트: 활성 자체관리 테넌트가 하나뿐일 때 그 테넌트(첫 설치면 1). 0개·여러 개면 아무것도 하지 않는다.</li>
 *   <li>라이선스: 파일이 주입됐고 대상 테넌트에 라이선스가 없을 때만 — 업로드 API 와 같은 {@code registerLicense}(형식·서명·제품 검증, 온라인이면 인증).</li>
 *   <li>office: 값이 주입됐고 대상 테넌트에 office 설정이 없을 때만 — 관리자 화면 PUT 과 같은 검증({@link OfficeUpdateRequest})을
 *       통과해야 통째로 저장한다. 하나라도 빠지면 아무것도 저장하지 않는다(반쪽 설정 방지).</li>
 * </ul>
 * 이미 값이 있으면 건드리지 않으므로 사실상 첫 설치 때 한 번만 들어간다. 실패해도 기동은 막지 않는다 — 관리자 화면에서 이어서 할 수 있다.
 */
@Slf4j
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class InitialSetupRunner implements ApplicationRunner {

    private final InitialSetupProperty property;
    private final TenantFindService tenantFindService;
    private final LicenseService licenseService;
    private final LicenseQueryRepository licenseQueryRepository;
    private final SystemConfigService systemConfigService;
    private final SystemConfigFindService systemConfigFindService;
    private final SystemConfigRepository systemConfigRepository;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        boolean hasLicense = StringUtils.hasText(property.licenseFile());
        boolean hasOffice = property.office() != null && StringUtils.hasText(property.office().domain());
        if (!hasLicense && !hasOffice) {
            log.info("license·office 초기 설정 주입값이 없어 건너뜀 — 관리자 화면에서 설정하세요.");
            return;
        }

        Integer tenantSeq = tenantFindService.findSingleActiveSelfManagedTenantSeq();
        if (tenantSeq == null) {
            log.warn("[ InitialSetupRunner ] 활성 자체관리 테넌트가 하나가 아니라 초기 설정을 건너뜀 — 관리자 화면에서 설정하세요.");
            return;
        }

        if (hasLicense) {
            try {
                setupLicense(tenantSeq);
            } catch (RuntimeException e) {
                log.error("[ InitialSetupRunner ] 라이선스 초기 등록 실패 — 관리자 화면에서 업로드하세요. tenantSeq : {}", tenantSeq, e);
            }
        }
        if (hasOffice) {
            try {
                setupOffice(tenantSeq);
            } catch (RuntimeException e) {
                log.error("[ InitialSetupRunner ] office 초기 설정 실패 — 관리자 화면에서 설정하세요. tenantSeq : {}", tenantSeq, e);
            }
        }
    }

    /** 대상 테넌트에 라이선스가 없을 때만 등록(업로드 API 와 같은 검증·온라인 인증). */
    void setupLicense(int tenantSeq) {
        if (licenseQueryRepository.findLicense(tenantSeq) != null) {
            log.info("[ InitialSetupRunner ] 라이선스가 이미 있어 초기 등록을 건너뜀. tenantSeq : {}", tenantSeq);
            return;
        }
        Path path = Path.of(property.licenseFile());
        if (!Files.isRegularFile(path)) {
            log.warn("[ InitialSetupRunner ] 라이선스 파일이 없어 초기 등록을 건너뜀. path : {}", path);
            return;
        }
        byte[] licenseBlob;
        try {
            licenseBlob = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("라이선스 파일을 읽지 못함: " + path, e);
        }
        licenseService.registerLicense(tenantSeq, licenseBlob);
        log.info("[ InitialSetupRunner ] 라이선스 초기 등록 완료. tenantSeq : {}, path : {}", tenantSeq, path);
    }

    /** 대상 테넌트에 office 설정이 없고, 주입값이 관리자 화면 검증을 통과할 때만 통째로 저장. */
    void setupOffice(int tenantSeq) {
        if (isOfficeConfigured(tenantSeq)) {
            log.info("[ InitialSetupRunner ] office 설정이 이미 있어 초기 설정을 건너뜀. tenantSeq : {}", tenantSeq);
            return;
        }

        InitialSetupProperty.Office office = property.office();
        OfficeUpdateRequest request = new OfficeUpdateRequest(
                trimToNull(office.domain()),
                trimToNull(office.authMode()),
                trimToNull(office.ticketServiceToken()),
                trimToNull(office.httpStorageRequestJwtSecret()),
                trimToNull(office.connectionType()),
                trimToNull(office.adapterName()));

        // 관리자 화면 PUT(@Valid OfficeUpdateRequest)과 같은 규칙 — 통과 못 하면 아무것도 저장하지 않는다
        Set<ConstraintViolation<OfficeUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.warn("[ InitialSetupRunner ] office 값이 관리자 화면 검증을 통과하지 못해 저장하지 않음: {}",
                    violations.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining(" / ")));
            return;
        }
        if (request.officeHttpStorageAdapterName() == null) {
            log.warn("[ InitialSetupRunner ] office 어댑터 이름이 없어 기본값(테넌트 번호 {})으로 저장 — TFO 의 어댑터 이름과 다르면 관리자 화면에서 고치세요.", tenantSeq);
        }

        systemConfigService.upsertOffice(tenantSeq, DriveConstants.SUPER_ADMIN_USER_SEQ, request);
        log.info("[ InitialSetupRunner ] office 초기 설정 완료. tenantSeq : {}, {}", tenantSeq, office);
    }

    /**
     * 관리자(또는 슈퍼관리자)가 이미 정한 office 설정이 있는지 — 주소가 있거나, 연결 방식을 저장했거나(사용 안 함 포함), 서비스 관리 테넌트면 true.
     * 예전 initialize.sql 이 넣던 빈 주소 행('')만 있는 것은 미설정으로 본다.
     */
    private boolean isOfficeConfigured(int tenantSeq) {
        if (StringUtils.hasText(systemConfigFindService.findOfficeConfig(tenantSeq).getOfficeDomain())) {
            return true;
        }
        if (systemConfigRepository.countByTenantAndKeys(tenantSeq, List.of(SystemConfigKey.OFFICE_CONNECTION_TYPE.getDbKey())) > 0) {
            return true;
        }
        return systemConfigFindService.isOfficeServiceManaged(tenantSeq);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
