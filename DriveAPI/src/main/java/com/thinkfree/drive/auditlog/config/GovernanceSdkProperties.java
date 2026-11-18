package com.thinkfree.drive.auditlog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 감사 로그 Governance 전송 설정(prefix {@code governance}). SDK 4값은 시크릿이라 환경변수로만 주입한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "governance")
public class GovernanceSdkProperties {

    private final Audit audit = new Audit();
    private final Origin origin = new Origin();
    private final Sdk sdk = new Sdk();

    @Getter
    @Setter
    public static class Audit {
        /** true일 때만 SDK 전송 구현체가 활성화된다. */
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Origin {
        /** EventIn origin.instanceId. 이 설치 인스턴스를 구분하는 안정된 식별자. */
        private String instanceId = "tfd";
    }

    @Getter
    @Setter
    public static class Sdk {
        private String endpoint;
        private String tenantId;
        private String keyId;
        private String base64Secret;
        private int connectTimeoutSeconds = 5;
        private int readTimeoutSeconds = 15;
    }
}
