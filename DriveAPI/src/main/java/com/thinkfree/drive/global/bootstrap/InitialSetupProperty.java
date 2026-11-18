package com.thinkfree.drive.global.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 설치 직후 1회성 초기 설정(외부 주입) — {@link InitialSetupRunner} 가 Flyway 마이그레이션 뒤 기동 시 적용한다.
 * 값이 비어 있으면 해당 항목은 건너뛴다. 이미 값이 있는 DB 는 덮어쓰지 않는다(관리자가 화면에서 바꾼 값 보존).
 * 대상 테넌트는 고정하지 않고 활성 자체관리 테넌트가 하나뿐일 때 그 테넌트로 정한다(첫 설치면 1).
 * 기본값은 application.yml {@code drive.initial-setup} 에 둔다.
 *
 * @param licenseFile 라이선스 파일(.tfl) 경로(설치기가 마운트) — 대상 테넌트에 라이선스가 없을 때만 등록
 * @param office office(TFO) 연결 — 대상 테넌트에 office 설정이 없을 때만 저장
 */
@ConfigurationProperties("drive.initial-setup")
public record InitialSetupProperty(
        String licenseFile,
        Office office
) {

    /**
     * office 연결 값 — 관리자 화면(OfficeUpdateRequest)과 같은 항목. 허용값 검증·저장 규칙은 OfficeUpdateRequest·SystemConfigService 가 정하고
     * 여기서는 주입된 값을 그대로 받는다.
     *
     * @param domain TFO 주소(http/https)
     * @param adapterName TFO 에 만든 HTTP Storage 어댑터 이름
     * @param authMode 인증 방식(yml 기본 ticket)
     * @param ticketServiceToken ticket 방식의 티켓 발급용 서비스 토큰
     * @param httpStorageRequestJwtSecret HTTP Storage 요청 서명키(32바이트 이상)
     * @param connectionType 연결 방식(yml 기본 onPremise)
     */
    public record Office(
            String domain,
            String adapterName,
            String authMode,
            String ticketServiceToken,
            String httpStorageRequestJwtSecret,
            String connectionType
    ) {
        @Override
        public String toString() {
            return "Office[domain=" + domain + ", adapterName=" + adapterName + ", authMode=" + authMode
                    + ", connectionType=" + connectionType + ", credentials=REDACTED]";
        }
    }
}
