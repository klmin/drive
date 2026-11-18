package com.thinkfree.drive.email.template.repository;

import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.query.EmailTemplateFindQuery;

/**
 * 이메일 템플릿 읽기(구 EmailTemplateMapper.xml findByTemplateKeyAndLocale) — {@code DefaultEmailTemplateFindService} 가 주입. 쓰기 없음. JPA(JPQL).
 */
public interface EmailTemplateQueryRepository {

    /** 템플릿 키 × {@code Locale.toLanguageTag()}(구 LocaleTypeHandler 동형) 단건의 제목·본문. 없으면 null, 키나 locale 이 null 이어도 null(구 XML 은 {@code = NULL} 로 0건). */
    EmailTemplateData findByTemplateKeyAndLocale(EmailTemplateFindQuery query);
}
