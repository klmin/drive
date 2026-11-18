package com.thinkfree.drive.global.config;

import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, SsoProviderScope.class, SsoProviderScope::from);
    }
}