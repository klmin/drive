package com.thinkfree.drive.weboffice.httpstorage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HttpStorageConfigurationTest {
    @Test void lifetimesRemainConfigurableWithoutSharedCredentials() throws Exception {
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test",
                Map.of("TFO_STORAGE_SESSION_TTL", "12h", "TFO_TICKET_OPEN_TTL", "90s")));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        var p = Binder.get(environment).bind("tfo.storage", Bindable.of(HttpStorageProperties.class)).get();
        assertEquals(Duration.ofHours(12), p.getSessionTtl());
        assertEquals(Duration.ofSeconds(90), p.getOpenTtl());
    }
    @Test void noProfileContainsGlobalOfficeCredentials() throws Exception {
        for (String file : new String[]{"application.yml"})   // 설정 파일을 application.yml 하나로 합침
            for (var source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                assertNull(source.getProperty("tfo.storage.adapter"));
                assertNull(source.getProperty("tfo.storage.request-jwt-secret"));
                assertNull(source.getProperty("tfo.storage.ticket-service-token"));
            }
    }
}
