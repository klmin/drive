package com.thinkfree.drive.email.template.converter;

import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import com.thinkfree.drive.email.template.query.EmailTemplateFindQuery;
import org.mapstruct.Mapper;

import java.util.Locale;

@Mapper(componentModel = "spring")
public interface EmailTemplateConverter {
    
    EmailTemplateFindQuery toEmailTemplateFindQuery(EmailTemplateKey templateKey, Locale locale);
}
