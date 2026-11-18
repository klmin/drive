package com.thinkfree.drive.email.template.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 이메일 템플릿 제목·본문 — 읽기 전용 값(구 MyBatis XML 이 SELECT 하던 두 컬럼). */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(EmailTemplateJpaRepository#findByTemplateKeyAndLocaleTag)
public class EmailTemplateData {

    private String subjectTemplate;
    private String bodyTemplate;
}
