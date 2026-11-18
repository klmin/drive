package com.thinkfree.drive.email.template.repository;

import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.entity.EmailTemplate;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * tb_email_template Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultEmailTemplateQueryRepository} 만 주입받아 위임한다.
 * 단건 조회는 JPQL 생성자 식(UNIQUE (template_key, locale_tag) 라 0 또는 1건). 쓰기 없음.
 */
interface EmailTemplateJpaRepository extends JpaRepository<EmailTemplate, Integer> {

    @Query("select new com.thinkfree.drive.email.template.data.EmailTemplateData(t.subjectTemplate, t.bodyTemplate) from EmailTemplate t "
            + "where t.templateKey = :templateKey and t.localeTag = :localeTag")
    Optional<EmailTemplateData> findByTemplateKeyAndLocaleTag(@Param("templateKey") EmailTemplateKey templateKey, @Param("localeTag") String localeTag);
}
