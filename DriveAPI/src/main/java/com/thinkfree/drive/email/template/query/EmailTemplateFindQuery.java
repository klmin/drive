package com.thinkfree.drive.email.template.query;

import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import lombok.Builder;
import lombok.Getter;

import java.util.Locale;

@Getter
@Builder
public class EmailTemplateFindQuery {
    private EmailTemplateKey templateKey;
    private Locale locale;
}
