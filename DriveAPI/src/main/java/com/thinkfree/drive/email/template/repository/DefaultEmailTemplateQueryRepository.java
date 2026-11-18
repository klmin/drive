package com.thinkfree.drive.email.template.repository;

import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.query.EmailTemplateFindQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Locale;

/**
 * tb_email_template 읽기 — 구 EmailTemplateMapper.xml 의 findByTemplateKeyAndLocale 을 JPA(JPQL 생성자 식)로(§1 v2 "JPA 우선"). 구 XML 과 같이 제목·본문 두 컬럼만 읽고, 엔티티를 영속 컨텍스트에 올리지 않는다(detach 불필요).
 * locale 은 구 {@code LocaleTypeHandler} 와 같이 {@link Locale#toLanguageTag()}(예: ko-KR) 로 비교한다.
 */
@Repository
@RequiredArgsConstructor
public class DefaultEmailTemplateQueryRepository implements EmailTemplateQueryRepository {

    private final EmailTemplateJpaRepository jpaRepository;

    @Override
    public EmailTemplateData findByTemplateKeyAndLocale(EmailTemplateFindQuery query) {
        Locale locale = query.getLocale();
        if (query.getTemplateKey() == null || locale == null) {
            return null;
        }
        return jpaRepository.findByTemplateKeyAndLocaleTag(query.getTemplateKey(), locale.toLanguageTag()).orElse(null);
    }
}
