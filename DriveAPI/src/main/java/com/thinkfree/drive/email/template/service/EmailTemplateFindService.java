package com.thinkfree.drive.email.template.service;

import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;

import java.util.Locale;

public interface EmailTemplateFindService {
    
    EmailTemplateData findByTemplateKeyAndLocale(EmailTemplateKey templateKey, Locale locale);
    
}
