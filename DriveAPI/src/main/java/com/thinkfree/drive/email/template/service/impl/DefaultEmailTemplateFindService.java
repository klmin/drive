package com.thinkfree.drive.email.template.service.impl;

import com.thinkfree.drive.email.template.converter.EmailTemplateConverter;
import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import com.thinkfree.drive.email.template.repository.EmailTemplateQueryRepository;
import com.thinkfree.drive.email.template.service.EmailTemplateFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DefaultEmailTemplateFindService implements EmailTemplateFindService {
    
    private final EmailTemplateConverter emailTemplateConverter;
    private final EmailTemplateQueryRepository emailTemplateQueryRepository;
    
    @Override
    public EmailTemplateData findByTemplateKeyAndLocale(EmailTemplateKey templateKey, Locale locale) {
        var emailTemplateFindQuery = emailTemplateConverter.toEmailTemplateFindQuery(templateKey, locale);
        return emailTemplateQueryRepository.findByTemplateKeyAndLocale(emailTemplateFindQuery);
    }
}
