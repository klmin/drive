package com.thinkfree.drive.license;

import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.service.LicenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class LicenseInitializer implements ApplicationRunner {

    private final LicenseService licenseService;
    private final LicenseQueryRepository licenseQueryRepository;
    private final LicenseAuthService licenseAuthService;

    @Override
    public void run(ApplicationArguments args) {
        // 라이선스가 등록된 모든 tenant 의 캐시를 warm-up.
        // 없으면 no-op — getLicense() 가 lazy 하게 로드한다.
        List<Integer> tenantSeqs = licenseQueryRepository.findAllLicensedTenantSeqs();
        if (tenantSeqs.isEmpty()) {
            log.info("[ LicenseInitializer ] no license registered yet — skip warm-up");
            return;
        }
        for (Integer tenantSeq : tenantSeqs) {
            if (tenantSeq != null) {
                licenseService.init(tenantSeq);
            }
        }

        // 온라인 라이선스는 기동할 때마다 인증한다 (비동기). 결과가 기록되면 캐시는 다음 조회에서 다시 읽힌다
        licenseAuthService.restartAll();
    }
}
