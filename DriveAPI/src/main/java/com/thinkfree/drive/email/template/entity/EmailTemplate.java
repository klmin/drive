package com.thinkfree.drive.email.template.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_email_template — 이메일 제목/본문 템플릿(템플릿 키 × BCP-47 locale_tag, UNIQUE). 시드 데이터만 있고 애플리케이션은 읽기만 한다.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선"): 단건 조회는 {@code EmailTemplateJpaRepository} JPQL 생성자 식({@code EmailTemplateData} — 제목·본문, 10-12). 쓰기 없음.
 * 날짜는 Instant(timestamptz 절대 시각, Hibernate 기본 매핑).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_email_template")
public class EmailTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer emailTemplateSeq;
    @Enumerated(EnumType.STRING)
    private EmailTemplateKey templateKey;
    private String localeTag;
    private String subjectTemplate;
    private String bodyTemplate;

}
