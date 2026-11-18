package com.thinkfree.drive.email.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailTemplateContext {
    
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper("${", "}");
    
    private final Map<String, String> params;
    private final Object[] subjectArgs;
    private final Object[] bodyArgs;
    
    public static EmailTemplateContext create(Map<String, String> params, List<String> subjectKeys, List<String> bodyKeys) {
        var subjectArgs = subjectKeys.stream().map(params::get).toArray();
        var bodyArgs = bodyKeys.stream().map(params::get).toArray();
        return new EmailTemplateContext(params, subjectArgs, bodyArgs);
    }
    
    public String renderTemplate(String template){
        return PLACEHOLDER_HELPER.replacePlaceholders(template, params::get);
    }
    
}
